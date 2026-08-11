package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.PublicSushiMeiApi
import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.local.IDeviceIdentityProvider
import com.restaurant.sushimei.frontend.data.local.ISecureSessionStore
import com.restaurant.sushimei.frontend.data.model.AuthResponseDto
import com.restaurant.sushimei.frontend.data.model.AuthenticatedUserDto
import com.restaurant.sushimei.frontend.data.model.LoginRequestDto
import com.restaurant.sushimei.frontend.data.model.RefreshRequestDto
import com.restaurant.sushimei.frontend.data.model.ApplicationRole
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Response

class FakeSessionStore : ISecureSessionStore {
    var storedSession: AuthResponseDto? = null
    override fun getSession(): AuthResponseDto? = storedSession
    override fun saveSession(s: AuthResponseDto) { storedSession = s }
    override fun clearSession() { storedSession = null }
}

class FakeDeviceIdentityManager : IDeviceIdentityProvider {
    override val deviceId: String get() = "device-123"
}

class FakeTimeProvider(var time: java.time.Instant) : ITimeProvider {
    override fun now(): java.time.Instant = time
}

class FakePublicApi(
    private val newSession: AuthResponseDto
) : PublicSushiMeiApi {
    var refreshCallCount = 0
    var exceptionToThrow: Exception? = null
    var responseToReturn: Response<AuthResponseDto>? = null

    override suspend fun login(request: LoginRequestDto): Response<AuthResponseDto> {
        return responseToReturn ?: Response.success(newSession)
    }

    override suspend fun refresh(request: RefreshRequestDto): Response<AuthResponseDto> {
        refreshCallCount++
        delay(50) // Simulate network delay
        if (exceptionToThrow != null) throw exceptionToThrow!!
        return responseToReturn ?: Response.success(newSession)
    }
}

class AuthRepositoryTest {
    private val fakeUser = AuthenticatedUserDto(1L, "admin", "Admin", ApplicationRole.OWNER, true, 1L)

    @Test
    fun `test single flight refresh concurrency`() = runBlocking {
        val oldSession = AuthResponseDto("old_access", "2030-01-01T00:00:00Z", "old_refresh", "2030-01-15T00:00:00Z", fakeUser)
        val newSession = AuthResponseDto("new_access", "2030-01-01T00:00:00Z", "new_refresh", "2030-01-15T00:00:00Z", fakeUser)

        val store = FakeSessionStore().apply { storedSession = oldSession }
        val api = FakePublicApi(newSession)
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager())
        val deferreds = (1..5).map {
            async { authRepository.refreshSession("old_access") }
        }

        val results = deferreds.awaitAll()

        results.forEach { token -> assertEquals("new_access", token) }
        assertEquals(1, api.refreshCallCount)
    }

    @Test
    fun `test ambiguous refresh exception clears local session`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        }
        val api = FakePublicApi(store.storedSession!!)
        api.exceptionToThrow = Exception("Ambiguous network failure")
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager())
        val result = authRepository.refreshSession("access")
        assertNull(result)
        assertNull(store.storedSession)
    }

    @Test
    fun `test 403 forbidden does not clear local session`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        }
        val api = FakePublicApi(store.storedSession!!)
        api.exceptionToThrow = ApiException("AUTH_FORBIDDEN", "Forbidden")
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager())
        val result = authRepository.refreshSession("access")
        assertNull(result)
        assertNotNull(store.storedSession)
    }

    @Test
    fun `test any non-403 auth exception clears local session`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        }
        val api = FakePublicApi(store.storedSession!!)
        api.exceptionToThrow = ApiException("SOME_OTHER_ERROR", "Error")
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager())
        val result = authRepository.refreshSession("access")
        assertNull(result)
        assertNull(store.storedSession)
    }

    @Test
    fun `test old refresh tokens are never reused after failure`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        }
        val api = FakePublicApi(store.storedSession!!)
        api.exceptionToThrow = Exception("Network failure")
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager())
        // First refresh fails ambiguously and clears the session
        authRepository.refreshSession("access")
        assertNull(store.storedSession)
        assertEquals(1, api.refreshCallCount)

        // Second refresh should immediately return null because there is no session,
        // without calling the API again.
        val result2 = authRepository.refreshSession(null)
        assertNull(result2)
        assertEquals(1, api.refreshCallCount) // Call count remains 1
    }

    @Test
    fun `test startup initialization with no session`() = runBlocking {
        val store = FakeSessionStore()
        val authRepository = AuthRepository(FakePublicApi(AuthResponseDto("", "", "", "", fakeUser)), store, FakeDeviceIdentityManager())
        authRepository.initialize()
        assertEquals(AuthState.Unauthenticated, authRepository.authState.value)
    }

    @Test
    fun `test startup initialization with valid session`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        }
        val timeProvider = FakeTimeProvider(java.time.Instant.parse("2021-01-01T00:00:00Z"))
        val authRepository = AuthRepository(FakePublicApi(store.storedSession!!), store, FakeDeviceIdentityManager(), timeProvider)
        authRepository.initialize()
        assert(authRepository.authState.value is AuthState.Authenticated)
    }
    fun `test startup gate expired session clears auth state`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2020-01-01T00:00:00Z", "refresh", "2020-01-01T00:00:00Z", fakeUser)
        }
        val timeProvider = FakeTimeProvider(java.time.Instant.parse("2021-01-01T00:00:00Z"))
        val authRepository = AuthRepository(FakePublicApi(store.storedSession!!), store, FakeDeviceIdentityManager(), timeProvider)
        authRepository.initialize()
        assertNull(store.storedSession)
        assertEquals(AuthState.Unauthenticated, authRepository.authState.value)
    }

    @Test
    fun `test startup gate expired access token triggers refresh`() = runBlocking {
        val oldSession = AuthResponseDto("old_access", "2021-01-01T00:00:00Z", "refresh", "2022-01-01T00:00:00Z", fakeUser)
        val newSession = AuthResponseDto("new_access", "2022-01-01T00:00:00Z", "new_refresh", "2022-01-01T00:00:00Z", fakeUser)
        val store = FakeSessionStore().apply { storedSession = oldSession }
        val api = FakePublicApi(newSession)
        val timeProvider = FakeTimeProvider(java.time.Instant.parse("2021-06-01T00:00:00Z"))
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager(), timeProvider)
        authRepository.initialize()
        assertEquals(1, api.refreshCallCount)
        assertEquals("new_access", store.storedSession?.accessToken)
        assert(authRepository.authState.value is AuthState.Authenticated)
    }
}

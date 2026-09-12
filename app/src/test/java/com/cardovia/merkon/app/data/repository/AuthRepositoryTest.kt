package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.api.PublicMerkonApi
import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.local.IDeviceIdentityProvider
import com.cardovia.merkon.app.data.local.ISecureSessionStore
import com.cardovia.merkon.app.data.model.AuthResponseDto
import com.cardovia.merkon.app.data.model.AuthenticatedUserDto
import com.cardovia.merkon.app.data.model.LoginRequestDto
import com.cardovia.merkon.app.data.model.RefreshRequestDto
import com.cardovia.merkon.app.data.model.ApplicationRole
import com.cardovia.merkon.app.data.model.RegistrationRequestDto
import com.cardovia.merkon.app.data.model.VerifyEmailRequestDto
import com.cardovia.merkon.app.data.model.ResendVerificationRequestDto
import com.cardovia.merkon.app.data.model.GenericMessageResponseDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.cardovia.merkon.app.data.local.IPendingRegistrationStore
import org.junit.Test
import io.mockk.mockk
import retrofit2.Response

class FakePendingStore : IPendingRegistrationStore {
    var clearCount = 0
    private val _pendingEmail = MutableStateFlow<String?>("old@example.com")
    override val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()
    override fun saveEmail(email: String) { _pendingEmail.value = email }
    override fun clear() {
        clearCount++
        _pendingEmail.value = null
    }
}

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
) : PublicMerkonApi {
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

    override suspend fun register(request: RegistrationRequestDto): Response<GenericMessageResponseDto> {
        return Response.success(202, GenericMessageResponseDto("Mock"))
    }

    override suspend fun verifyEmail(request: VerifyEmailRequestDto): Response<GenericMessageResponseDto> {
        return Response.success(200, GenericMessageResponseDto("Mock"))
    }

    override suspend fun resendVerification(request: ResendVerificationRequestDto): Response<GenericMessageResponseDto> {
        return Response.success(202, GenericMessageResponseDto("Mock"))
    }

    override suspend fun requestPasswordRecovery(request: com.cardovia.merkon.app.data.model.PasswordRecoveryRequestDto): Response<com.cardovia.merkon.app.data.model.GenericMessageResponseDto> {
        return Response.success(202, com.cardovia.merkon.app.data.model.GenericMessageResponseDto("Mock"))
    }

    override suspend fun confirmPasswordRecovery(request: com.cardovia.merkon.app.data.model.PasswordRecoveryConfirmRequestDto): Response<Void> {
        return Response.success<Void>(204, null)
    }
}

class AuthRepositoryTest {
    private val fakeUser = AuthenticatedUserDto(1L, "admin", "Admin", ApplicationRole.OWNER, true, 1L)

    @Test
    fun `login success clears exactly once`() = runBlocking {
        val session = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        val api = FakePublicApi(session)
        val pendingStore = FakePendingStore()
        val repo = AuthRepository(api, FakeSessionStore(), FakeDeviceIdentityManager(), FakeTimeProvider(java.time.Instant.now()), pendingStore)

        repo.login("u", "p")
        assertEquals(1, pendingStore.clearCount)
    }

    @Test
    fun `login failure does not clear pending store`() = runBlocking {
        val session = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        val api = FakePublicApi(session)
        api.responseToReturn = Response.error(401, okhttp3.ResponseBody.create(null, ""))
        val pendingStore = FakePendingStore()
        val repo = AuthRepository(api, FakeSessionStore(), FakeDeviceIdentityManager(), FakeTimeProvider(java.time.Instant.now()), pendingStore)

        repo.login("u", "p")
        assertEquals(0, pendingStore.clearCount)
    }

    @Test
    fun `initialize with valid non-expired session clears exactly once`() = runBlocking {
        val session = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        val store = FakeSessionStore().apply { storedSession = session }
        val pendingStore = FakePendingStore()
        val timeProvider = FakeTimeProvider(java.time.Instant.parse("2025-01-01T00:00:00Z"))
        val repo = AuthRepository(FakePublicApi(session), store, FakeDeviceIdentityManager(), timeProvider, pendingStore)

        repo.initialize()
        assertEquals(1, pendingStore.clearCount)
    }

    @Test
    fun `initialize with no stored session does not clear pending store`() = runBlocking {
        val store = FakeSessionStore() // No session
        val pendingStore = FakePendingStore()
        val repo = AuthRepository(FakePublicApi(mockk(relaxed = true)), store, FakeDeviceIdentityManager(), FakeTimeProvider(java.time.Instant.now()), pendingStore)

        repo.initialize()
        assertEquals(0, pendingStore.clearCount)
    }

    @Test
    fun `initialize with expired access but successful refresh clears exactly once`() = runBlocking {
        val oldSession = AuthResponseDto("access", "2020-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        val newSession = AuthResponseDto("new_access", "2030-01-01T00:00:00Z", "new_refresh", "2030-01-15T00:00:00Z", fakeUser)
        val store = FakeSessionStore().apply { storedSession = oldSession }
        val pendingStore = FakePendingStore()
        val timeProvider = FakeTimeProvider(java.time.Instant.parse("2025-01-01T00:00:00Z"))
        val repo = AuthRepository(FakePublicApi(newSession), store, FakeDeviceIdentityManager(), timeProvider, pendingStore)

        repo.initialize()
        // initialize -> refreshSession -> clears
        assertEquals(1, pendingStore.clearCount)
    }

    @Test
    fun `direct successful refreshSession clears exactly once`() = runBlocking {
        val session = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        val store = FakeSessionStore().apply { storedSession = session }
        val pendingStore = FakePendingStore()
        val repo = AuthRepository(FakePublicApi(session), store, FakeDeviceIdentityManager(), FakeTimeProvider(java.time.Instant.now()), pendingStore)

        repo.refreshSession("access")
        assertEquals(1, pendingStore.clearCount)
    }

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
        val api = FakePublicApi(AuthResponseDto("", "", "", "", fakeUser))
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager())
        authRepository.initialize()
        assertEquals(AuthState.Unauthenticated, authRepository.authState.value)
        assertEquals(0, api.refreshCallCount)
    }

    @Test
    fun `test startup initialization with valid session`() = runBlocking {
        val store = FakeSessionStore().apply {
            storedSession = AuthResponseDto("access", "2030-01-01T00:00:00Z", "refresh", "2030-01-15T00:00:00Z", fakeUser)
        }
        val api = FakePublicApi(store.storedSession!!)
        val timeProvider = FakeTimeProvider(java.time.Instant.parse("2021-01-01T00:00:00Z"))
        val authRepository = AuthRepository(api, store, FakeDeviceIdentityManager(), timeProvider)
        authRepository.initialize()
        assert(authRepository.authState.value is AuthState.Authenticated)
        assertEquals(0, api.refreshCallCount)
    }

    @Test
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

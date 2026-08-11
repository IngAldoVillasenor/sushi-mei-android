package com.restaurant.sushimei.frontend.data.repository

import android.annotation.SuppressLint
import com.restaurant.sushimei.frontend.data.api.PublicSushiMeiApi
import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.local.IDeviceIdentityProvider
import com.restaurant.sushimei.frontend.data.local.ISecureSessionStore
import com.restaurant.sushimei.frontend.data.model.AuthenticatedUserDto
import com.restaurant.sushimei.frontend.data.model.LoginRequestDto
import com.restaurant.sushimei.frontend.data.model.RefreshRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthState {
    object Initializing : AuthState
    object Unauthenticated : AuthState
    data class Authenticated(val user: AuthenticatedUserDto) : AuthState
}

interface ITimeProvider {
    @SuppressLint("NewApi")
    fun now(): java.time.Instant = java.time.Instant.now()
}

/**
 * Centralized Session Manager & Auth Repository.
 * Handles Single-Flight token refresh and API authentication logic.
 */
@SuppressLint("NewApi")
class AuthRepository(
    private val publicApi: PublicSushiMeiApi,
    private val sessionStore: ISecureSessionStore,
    private val deviceIdentityManager: IDeviceIdentityProvider,
    private val timeProvider: ITimeProvider = object : ITimeProvider {}
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Single-Flight Mutex for refreshing tokens
    private val refreshMutex = Mutex()

    suspend fun initialize() {
        val session = sessionStore.getSession()
        if (session == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        try {
            val now = timeProvider.now()
            val sessionExpiresAt = java.time.Instant.parse(session.sessionExpiresAt)
            if (sessionExpiresAt <= now) {
                clearSession()
                _authState.value = AuthState.Unauthenticated
                return
            }
            // Validate the persisted access-token expiration returned by AuthResponse.
            val accessExpiresAt = java.time.Instant.parse(session.accessTokenExpiresAt)
            if (accessExpiresAt <= now) {
                // Access token expired, attempt one safe refresh
                val newToken = refreshSession(session.accessToken)
                if (newToken == null) {
                    _authState.value = AuthState.Unauthenticated
                }
            } else {
                _authState.value = AuthState.Authenticated(session.user)
            }
        } catch (e: Exception) {
            // Parsing failure or unexpected error
            clearSession()
            _authState.value = AuthState.Unauthenticated
        }
    }

    @Throws(Exception::class)
    suspend fun login(username: String, password: String): Boolean {
        val request = LoginRequestDto(
            username = username,
            password = password,
            deviceId = deviceIdentityManager.deviceId,
            deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            appVersion = com.restaurant.sushimei.frontend.BuildConfig.VERSION_NAME
        )
        val response = publicApi.login(request)
        if (response.isSuccessful && response.body() != null) {
            val authResponse = response.body()!!
            sessionStore.saveSession(authResponse)
            _authState.value = AuthState.Authenticated(authResponse.user)
            return true
        } else {
            return false
        }
    }

    suspend fun logout(authenticatedApi: SushiMeiApi) {
        try {
            // Try to notify backend
            authenticatedApi.logout()
        } catch (e: Exception) {
            // Ignore network errors on logout, we must clear local session anyway
        } finally {
            clearSession()
        }
    }

    fun clearSession() {
        sessionStore.clearSession()
        _authState.value = AuthState.Unauthenticated
    }

    fun getAccessToken(): String? {
        return sessionStore.getSession()?.accessToken
    }

    /**
     * Performs a Single-Flight refresh.
     * Safe to be called by OkHttp Authenticator concurrently.
     * Returns the new accessToken if successful, null if failed.
     */
    suspend fun refreshSession(failedAccessToken: String?): String? {
        refreshMutex.withLock {
            // Re-read the current session inside the lock
            val currentSession = sessionStore.getSession()
            if (currentSession == null) {
                _authState.value = AuthState.Unauthenticated
                return null
            }
            // If another caller already replaced the failed access token:
            if (failedAccessToken != null && currentSession.accessToken != failedAccessToken) {
                // Token already rotated by someone else!
                return currentSession.accessToken
            }

            try {
                val request = RefreshRequestDto(
                    refreshToken = currentSession.refreshToken,
                    deviceId = deviceIdentityManager.deviceId
                )
                val response = publicApi.refresh(request)
                if (response.isSuccessful && response.body() != null) {
                    val newSession = response.body()!!
                    sessionStore.saveSession(newSession)
                    _authState.value = AuthState.Authenticated(newSession.user)
                    return newSession.accessToken
                } else {
                    clearSession()
                    return null
                }
            } catch (e: com.restaurant.sushimei.frontend.data.api.ApiException) {
                if (e.code == "AUTH_FORBIDDEN") {
                    return null
                }
                clearSession()
                return null
            } catch (e: Exception) {
                clearSession()
                return null
            }
        }
    }
}

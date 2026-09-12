package com.cardovia.merkon.app.ui.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.cardovia.merkon.app.data.model.DeepLinkEvent
import com.cardovia.merkon.app.data.repository.AuthRepository
import com.cardovia.merkon.app.data.repository.AuthState
import com.cardovia.merkon.app.ui.MainScreen
import com.cardovia.merkon.app.ui.screens.password_recovery.PasswordRecoveryRequestScreen
import com.cardovia.merkon.app.ui.screens.password_recovery.PasswordResetScreen

class AuthGateCoordinator(
    private val authRepository: AuthRepository,
    private val onDeepLinkEventConsumed: () -> Unit
) {
    var activeGateState by mutableStateOf<String?>(null)

    fun onNewDeepLinkEvent(event: DeepLinkEvent?, authState: AuthState) {
        if (event is DeepLinkEvent.PasswordReset) {
            activeGateState = "password_reset"
        } else if (event is DeepLinkEvent.Verification) {
            if (authState is AuthState.Authenticated) {
                onDeepLinkEventConsumed() // discard silently when authenticated
            }
        }
    }

    fun consumeDeepLinkEvent() {
        onDeepLinkEventConsumed()
    }

    fun onPasswordResetSuccess() {
        consumeDeepLinkEvent()
        authRepository.clearSession()
        activeGateState = "password_reset_success"
    }

    fun onPasswordResetInvalidToken() {
        consumeDeepLinkEvent()
        activeGateState = "password_reset_invalid"
    }

    fun onPasswordResetNavigateToRequest() {
        consumeDeepLinkEvent()
        authRepository.clearSession()
        activeGateState = "forgot_password"
    }

    fun onPasswordResetBackToLogin() {
        consumeDeepLinkEvent()
        authRepository.clearSession()
        activeGateState = null
    }
}

@Composable
fun rememberAuthGateCoordinator(
    authRepository: AuthRepository,
    onDeepLinkEventConsumed: () -> Unit
): AuthGateCoordinator {
    return remember { AuthGateCoordinator(authRepository, onDeepLinkEventConsumed) }
}

@Composable
fun AuthGateScreen(
    authRepository: AuthRepository,
    deepLinkEvent: DeepLinkEvent? = null,
    onDeepLinkEventConsumed: () -> Unit = {}
) {
    val authState by authRepository.authState.collectAsState()
    val coordinator = rememberAuthGateCoordinator(authRepository, onDeepLinkEventConsumed)

    LaunchedEffect(authRepository) {
        authRepository.initialize()
    }

    LaunchedEffect(deepLinkEvent, authState) {
        coordinator.onNewDeepLinkEvent(deepLinkEvent, authState)
    }

    if (coordinator.activeGateState == "password_reset" ||
        coordinator.activeGateState == "password_reset_success" ||
        coordinator.activeGateState == "password_reset_invalid"
    ) {
        PasswordResetScreen(
            deepLinkToken = (deepLinkEvent as? DeepLinkEvent.PasswordReset)?.token,
            onDeepLinkTokenConsumed = coordinator::consumeDeepLinkEvent,
            onResetSuccess = {
                coordinator.activeGateState = null
            },
            onNavigateToRequest = coordinator::onPasswordResetNavigateToRequest,
            onBackToLogin = coordinator::onPasswordResetBackToLogin,
            onBackendSuccess = coordinator::onPasswordResetSuccess,
            onBackendInvalidToken = coordinator::onPasswordResetInvalidToken,
            isAlreadySuccessful = coordinator.activeGateState == "password_reset_success",
            isAlreadyInvalid = coordinator.activeGateState == "password_reset_invalid"
        )
    } else if (coordinator.activeGateState == "forgot_password") {
        PasswordRecoveryRequestScreen(
            onBackToLogin = { coordinator.activeGateState = null }
        )
    } else {
        when (val state = authState) {
            is AuthState.Initializing -> {
                // Keep empty or show splash
            }
            is AuthState.Unauthenticated -> {
                UnauthenticatedGate(
                    authRepository = authRepository,
                    deepLinkEvent = deepLinkEvent,
                    onDeepLinkEventConsumed = onDeepLinkEventConsumed,
                    onNavigateToForgotPassword = { coordinator.activeGateState = "forgot_password" }
                )
            }
            is AuthState.Authenticated -> {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    com.cardovia.merkon.app.data.local.providePrintManager(context.applicationContext)
                }
                MainScreen(authRepository = authRepository, user = state.user)
            }
        }
    }
}

@Composable
fun UnauthenticatedGate(
    authRepository: AuthRepository,
    deepLinkEvent: DeepLinkEvent?,
    onDeepLinkEventConsumed: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val context = LocalContext.current
    val pendingStore = remember { com.cardovia.merkon.app.data.local.providePendingRegistrationStore(context) }
    val pendingEmail by pendingStore.pendingEmail.collectAsState()

    var activeScreen by rememberSaveable { mutableStateOf("login") }
    var prefillEmail by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(deepLinkEvent) {
        if (deepLinkEvent is DeepLinkEvent.Verification) {
            activeScreen = "verification"
        }
    }

    if (activeScreen == "verification" || pendingEmail != null || deepLinkEvent is DeepLinkEvent.Verification) {
        PendingVerificationScreen(
            email = pendingEmail,
            deepLinkToken = (deepLinkEvent as? DeepLinkEvent.Verification)?.token,
            onDeepLinkTokenConsumed = onDeepLinkEventConsumed,
            onVerificationSuccess = {
                if (pendingEmail != null) prefillEmail = pendingEmail!!
                pendingStore.clear()
                activeScreen = "login"
            },
            onBackToLogin = {
                pendingStore.clear()
                activeScreen = "login"
            }
        )
    } else if (activeScreen == "registration") {
        RegistrationScreen(
            onBack = { activeScreen = "login" },
            onRegistrationSuccess = { email ->
                pendingStore.saveEmail(email)
                activeScreen = "verification"
            }
        )
    } else {
        LoginScreen(
            authRepository = authRepository,
            prefillEmail = if (prefillEmail.isNotEmpty()) prefillEmail else (pendingEmail ?: ""),
            onNavigateToRegistration = { activeScreen = "registration" },
            onNavigateToForgotPassword = onNavigateToForgotPassword
        )
    }
}

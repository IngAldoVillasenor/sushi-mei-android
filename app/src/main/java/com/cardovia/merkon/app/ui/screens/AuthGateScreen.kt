package com.cardovia.merkon.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cardovia.merkon.app.data.local.PendingRegistrationStore
import com.cardovia.merkon.app.data.repository.AuthRepository
import com.cardovia.merkon.app.data.repository.AuthState
import com.cardovia.merkon.app.ui.MainScreen

@Composable
fun AuthGateScreen(
    authRepository: AuthRepository,
    deepLinkToken: String? = null,
    onDeepLinkTokenConsumed: () -> Unit = {}
) {
    val authState by authRepository.authState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(authRepository) {
        authRepository.initialize()
    }

    when (val state = authState) {
        is AuthState.Initializing -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Unauthenticated -> {
            UnauthenticatedGate(
                authRepository = authRepository,
                deepLinkToken = deepLinkToken,
                onDeepLinkTokenConsumed = onDeepLinkTokenConsumed
            )
        }
        is AuthState.Authenticated -> {
            val context = LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                com.cardovia.merkon.app.data.local.providePrintManager(context.applicationContext)
            }
            androidx.compose.runtime.LaunchedEffect(deepLinkToken) {
                if (deepLinkToken != null) {
                    onDeepLinkTokenConsumed()
                }
            }
            MainScreen(authRepository = authRepository, user = state.user)
        }
    }
}

@Composable
fun UnauthenticatedGate(
    authRepository: AuthRepository,
    deepLinkToken: String?,
    onDeepLinkTokenConsumed: () -> Unit
) {
    val context = LocalContext.current
    val pendingStore = remember { com.cardovia.merkon.app.data.local.providePendingRegistrationStore(context) }
    val pendingEmail by pendingStore.pendingEmail.collectAsState()

    var activeScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("login") }
    var prefillEmail by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(deepLinkToken) {
        if (deepLinkToken != null) {
            activeScreen = "verification"
        }
    }

    if (activeScreen == "verification" || pendingEmail != null || deepLinkToken != null) {
        PendingVerificationScreen(
            email = pendingEmail,
            deepLinkToken = deepLinkToken,
            onDeepLinkTokenConsumed = onDeepLinkTokenConsumed,
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
            prefillEmail = prefillEmail,
            onNavigateToRegistration = { activeScreen = "registration" }
        )
    }
}

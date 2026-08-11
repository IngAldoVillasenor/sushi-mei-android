package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.restaurant.sushimei.frontend.data.repository.AuthRepository
import com.restaurant.sushimei.frontend.data.repository.AuthState
import com.restaurant.sushimei.frontend.ui.MainScreen

@Composable
fun AuthGateScreen(authRepository: AuthRepository) {
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
            LoginScreen(authRepository = authRepository)
        }
        is AuthState.Authenticated -> {
            // Pasamos el authRepository por si AccountScreen lo necesita para logout
            MainScreen(authRepository = authRepository, user = state.user)
        }
    }
}

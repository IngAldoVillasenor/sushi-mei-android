package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.AuthenticatedUserDto
import com.restaurant.sushimei.frontend.data.repository.AuthRepository
import com.restaurant.sushimei.frontend.data.api.NetworkModule
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    authRepository: AuthRepository,
    user: AuthenticatedUserDto,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToSessions: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoggingOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Mi Cuenta", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Nombre: ${user.displayName}", style = MaterialTheme.typography.bodyLarge)
                Text("Usuario: ${user.username}", style = MaterialTheme.typography.bodyLarge)
                Text("Rol: ${user.role}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToChangePassword,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Cambiar Contraseña")
        }

        Button(
            onClick = onNavigateToSessions,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Mis Sesiones")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                isLoggingOut = true
                coroutineScope.launch {
                    authRepository.logout(NetworkModule.sushiMeiApi)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            enabled = !isLoggingOut
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onError,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Cerrar Sesión")
            }
        }
    }
}

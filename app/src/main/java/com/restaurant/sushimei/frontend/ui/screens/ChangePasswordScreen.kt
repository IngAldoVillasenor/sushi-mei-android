package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.api.NetworkModule
import com.restaurant.sushimei.frontend.data.model.ChangePasswordRequestDto
import com.restaurant.sushimei.frontend.data.repository.AuthRepository
import kotlinx.coroutines.launch
import com.restaurant.sushimei.frontend.data.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    authRepository: AuthRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val isSubmitEnabled = currentPassword.isNotBlank() &&
                          newPassword.isNotBlank() &&
                          confirmPassword.isNotBlank() &&
                          !isLoading

    val onSubmit: () -> Unit = {
        keyboardController?.hide()
        if (newPassword != confirmPassword) {
            errorMessage = "Las contraseñas nuevas no coinciden."
        } else {
            isLoading = true
            errorMessage = null
            successMessage = null
            coroutineScope.launch {
                try {
                    val api = NetworkModule.sushiMeiApi
                    val response = api.changePassword(
                        ChangePasswordRequestDto(currentPassword, newPassword)
                    )
                    if (response.isSuccessful) {
                        successMessage = "Contraseña cambiada. Cerrando sesión..."
                        kotlinx.coroutines.delay(1500)
                        authRepository.clearSession()
                    } else {
                        errorMessage = "Error al cambiar contraseña."
                    }
                } catch (e: ApiException) {
                    if (e.code == "AUTH_PASSWORD_REJECTED") {
                        errorMessage = e.message ?: "La nueva contraseña no cumple los requisitos."
                    } else {
                        errorMessage = e.message ?: "Error al cambiar contraseña."
                    }
                } catch (e: Exception) {
                    errorMessage = "Error de red o servicio no disponible."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar Contraseña") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (successMessage != null) {
                        Text(
                            text = successMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Contraseña actual") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
                            trailingIcon = {
                                val image = if (currentPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                                    Icon(image, "Toggle visibility")
                                }
                            }
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Nueva contraseña") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
                            trailingIcon = {
                                val image = if (newPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                    Icon(image, "Toggle visibility")
                                }
                            }
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirmar nueva contraseña") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (isSubmitEnabled) onSubmit() })
                        )
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = onSubmit,
                            enabled = isSubmitEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Cambiar Contraseña")
                            }
                        }
                    }
                }
            }
        }
    }
}

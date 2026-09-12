package com.cardovia.merkon.app.ui.screens.password_recovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.NetworkModule
import com.cardovia.merkon.app.data.model.PasswordRecoveryConfirmRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PasswordResetViewModel(
    private val api: com.cardovia.merkon.app.data.api.PublicMerkonApi = NetworkModule.publicMerkonApi
) : androidx.lifecycle.ViewModel() {
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isSuccess by mutableStateOf(false)
    var isInvalidToken by mutableStateOf(false)

    fun resetState() {
        password = ""
        confirmPassword = ""
        isLoading = false
        errorMessage = null
        isSuccess = false
        isInvalidToken = false
    }

    fun submitReset(
        token: String,
        onSuccessAction: () -> Unit,
        onInvalidTokenAction: () -> Unit
    ) {
        if (password != confirmPassword) {
            errorMessage = "Las contraseñas no coinciden."
            return
        }
        if (password.isBlank()) {
            errorMessage = "La contraseña no puede estar vacía."
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val response = api.confirmPasswordRecovery(PasswordRecoveryConfirmRequestDto(token, password))
                if (response.isSuccessful && response.code() == 204) {
                    password = ""
                    confirmPassword = ""
                    isSuccess = true
                    onSuccessAction()
                } else {
                    errorMessage = "Error al restablecer la contraseña."
                }
            } catch (e: ApiException) {
                if (e.code == "PASSWORD_RECOVERY_INVALID_TOKEN") {
                    password = ""
                    confirmPassword = ""
                    isInvalidToken = true
                    errorMessage = "El enlace de restablecimiento no es válido o ha vencido."
                    onInvalidTokenAction()
                } else {
                    errorMessage = when {
                        e.code == "AUTH_PASSWORD_REJECTED" -> "La contraseña no cumple con los requisitos de seguridad."
                        e.httpStatus == 429 -> "Demasiados intentos. Por favor, espera antes de intentar de nuevo."
                        else -> "Error de validación o del servidor."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Error de conexión."
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
fun PasswordResetScreen(
    deepLinkToken: String?,
    onDeepLinkTokenConsumed: () -> Unit,
    onResetSuccess: () -> Unit,
    onNavigateToRequest: () -> Unit,
    onBackToLogin: () -> Unit,
    onBackendSuccess: () -> Unit,
    onBackendInvalidToken: () -> Unit,
    isAlreadySuccessful: Boolean = false,
    isAlreadyInvalid: Boolean = false,
    viewModel: PasswordResetViewModel = viewModel()
) {
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkToken) {
        if (deepLinkToken != null) {
            viewModel.resetState()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
                Text(
                    text = "Nueva contraseña",
                    style = MaterialTheme.typography.titleLarge
                )

                if (viewModel.isSuccess || isAlreadySuccessful) {
                    Text(
                        text = "Tu contraseña se actualizó correctamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = androidx.compose.ui.graphics.Color(0xFF0F3D37)
                    )
                    Button(
                        onClick = {
                            viewModel.resetState()
                            onResetSuccess()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar sesión")
                    }
                } else if (viewModel.isInvalidToken || isAlreadyInvalid) {
                    Text(
                        text = viewModel.errorMessage ?: "El enlace de restablecimiento no es válido o ha vencido.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            viewModel.resetState()
                            onNavigateToRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Solicitar nuevo enlace")
                    }
                    TextButton(
                        onClick = {
                            viewModel.resetState()
                            onBackToLogin()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver a login")
                    }
                } else {
                    OutlinedTextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = { Text("Nueva contraseña") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null)
                            }
                        },
                        enabled = !viewModel.isLoading
                    )

                    OutlinedTextField(
                        value = viewModel.confirmPassword,
                        onValueChange = { viewModel.confirmPassword = it },
                        label = { Text("Confirmar contraseña") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        enabled = !viewModel.isLoading
                    )

                    if (viewModel.errorMessage != null) {
                        Text(
                            text = viewModel.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            if (deepLinkToken != null) {
                                viewModel.submitReset(
                                    token = deepLinkToken,
                                    onSuccessAction = onBackendSuccess,
                                    onInvalidTokenAction = onBackendInvalidToken
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isLoading
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Guardar contraseña")
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.resetState()
                            onDeepLinkTokenConsumed()
                            onBackToLogin()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isLoading
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

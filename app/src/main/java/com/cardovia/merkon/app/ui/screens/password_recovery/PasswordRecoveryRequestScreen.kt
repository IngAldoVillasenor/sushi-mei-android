package com.cardovia.merkon.app.ui.screens.password_recovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.NetworkModule
import com.cardovia.merkon.app.data.model.PasswordRecoveryRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PasswordRecoveryRequestViewModel(
    private val api: com.cardovia.merkon.app.data.api.PublicMerkonApi = NetworkModule.publicMerkonApi
) : androidx.lifecycle.ViewModel() {
    var emailInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isSuccess by mutableStateOf(false)

    fun reset() {
        emailInput = ""
        isLoading = false
        errorMessage = null
        isSuccess = false
    }

    fun submitRequest() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val response = api.requestPasswordRecovery(PasswordRecoveryRequestDto(emailInput.trim()))
                if (response.isSuccessful && response.code() == 202) {
                    isSuccess = true
                } else {
                    errorMessage = "Error al solicitar la recuperación."
                }
            } catch (e: ApiException) {
                errorMessage = when {
                    e.code == "PASSWORD_RECOVERY_INVALID_REQUEST" -> "La solicitud de recuperación no es válida."
                    e.code == "PASSWORD_RECOVERY_RATE_LIMITED" -> "Demasiados intentos. Por favor, espera antes de intentar de nuevo."
                    e.httpStatus == 429 -> "Demasiados intentos. Por favor, espera antes de intentar de nuevo."
                    else -> "Error de validación o del servidor."
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
fun PasswordRecoveryRequestScreen(
    onBackToLogin: () -> Unit,
    viewModel: PasswordRecoveryRequestViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.reset()
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
                    text = "Recuperar contraseña",
                    style = MaterialTheme.typography.titleLarge
                )

                if (viewModel.isSuccess) {
                    Text(
                        text = "Si la cuenta es válida, recibirás un correo con el enlace para restablecer tu contraseña.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onBackToLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver a login")
                    }
                    TextButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Intentar de nuevo")
                    }
                } else {
                    Text(
                        text = "Ingresa tu correo electrónico para recibir un enlace de restablecimiento.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = viewModel.emailInput,
                        onValueChange = { viewModel.emailInput = it },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
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
                            if (viewModel.emailInput.isNotBlank()) {
                                viewModel.submitRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isLoading && viewModel.emailInput.isNotBlank()
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Enviar enlace")
                        }
                    }

                    TextButton(
                        onClick = onBackToLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isLoading
                    ) {
                        Text("Volver a login")
                    }
                }
            }
        }
    }
}

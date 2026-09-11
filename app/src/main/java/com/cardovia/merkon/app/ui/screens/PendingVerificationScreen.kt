package com.cardovia.merkon.app.ui.screens

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
import kotlinx.coroutines.CancellationException
import com.cardovia.merkon.app.BuildConfig
import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.NetworkModule
import com.cardovia.merkon.app.data.model.ResendVerificationRequestDto
import com.cardovia.merkon.app.data.model.VerifyEmailRequestDto
import com.cardovia.merkon.app.util.VerificationTokenParser
import kotlinx.coroutines.launch

class PendingVerificationViewModel(
    private val api: com.cardovia.merkon.app.data.api.PublicMerkonApi = NetworkModule.publicMerkonApi
) : androidx.lifecycle.ViewModel() {
    var tokenInput by mutableStateOf("")
    var emailInput by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)
    var isVerified by mutableStateOf(false)

    fun syncExternalEmail(email: String?) {
        emailInput = email ?: ""
    }

    fun reset() {
        tokenInput = ""
        emailInput = ""
        isLoading = false
        errorMessage = null
        successMessage = null
        isVerified = false
    }

    fun verify(rawInput: String) {
        viewModelScope.launch {
        isLoading = true
        errorMessage = null
        successMessage = null
        isVerified = false

        val capturedInput = rawInput
        tokenInput = ""

        try {
            val token = VerificationTokenParser.parse(capturedInput, BuildConfig.VERIFICATION_HOST)
            if (token == null) {
                errorMessage = "El enlace o código de verificación es inválido."
                return@launch
            }

            val response = api.verifyEmail(VerifyEmailRequestDto(token))

            if (response.isSuccessful && response.code() == 200) {
                isVerified = true
            } else {
                errorMessage = "Error de validación o del servidor."
            }
        } catch (e: ApiException) {
            errorMessage = when {
                e.code == "EMAIL_VERIFICATION_INVALID_TOKEN" -> "El enlace o token de verificación es inválido o ha expirado."
                e.code == "EMAIL_VERIFICATION_INVALID_REQUEST" -> "La solicitud de verificación no es válida."
                e.httpStatus == 429 -> "Demasiados intentos. Por favor, espera antes de intentar de nuevo."
                else -> "Error de validación del token."
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

    fun resend(email: String) {
        viewModelScope.launch {
        isLoading = true
        errorMessage = null
        successMessage = null
        try {
            val response = api.resendVerification(ResendVerificationRequestDto(email))
            if (response.isSuccessful && response.code() == 202) {
                successMessage = "Si la cuenta requiere verificación, enviaremos un nuevo correo."
            } else {
                errorMessage = "Error al reenviar el correo. Inténtalo más tarde."
            }
        } catch (e: ApiException) {
            errorMessage = when {
                e.code == "EMAIL_VERIFICATION_RESEND_RATE_LIMITED" -> "Demasiados reenvíos. Por favor, espera."
                e.httpStatus == 429 -> "Demasiados intentos. Por favor, espera antes de intentar de nuevo."
                else -> "Error al reenviar el correo."
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
fun PendingVerificationScreen(
    email: String?,
    deepLinkToken: String? = null,
    onDeepLinkTokenConsumed: () -> Unit = {},
    onVerificationSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: PendingVerificationViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    var showTokenInput by remember { mutableStateOf(false) }

    LaunchedEffect(email) {
        viewModel.syncExternalEmail(email)
    }

    LaunchedEffect(deepLinkToken) {
        if (!deepLinkToken.isNullOrBlank()) {
            val token = deepLinkToken
            onDeepLinkTokenConsumed()
            viewModel.verify(token)
        }
    }



    if (viewModel.isVerified) {
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
                        text = "Correo verificado correctamente.",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = androidx.compose.ui.graphics.Color(0xFF0F3D37)
                    )
                    Button(
                        onClick = {
                            viewModel.reset()
                            onVerificationSuccess()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar sesión")
                    }
                }
            }
        }
        return
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
                    text = "Verifica tu correo",
                    style = MaterialTheme.typography.titleLarge
                )

                if (email != null) {
                    Text(
                        text = "Si el registro puede continuar, recibirás un correo en:\n\n$email\n\nPor favor, haz clic en el enlace del correo para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Verificando token...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                if (showTokenInput || email == null) {
                    OutlinedTextField(
                        value = viewModel.tokenInput,
                        onValueChange = { viewModel.tokenInput = it },
                        label = { Text("URL completa o código de verificación") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        enabled = !viewModel.isLoading
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.verify(viewModel.tokenInput)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = viewModel.tokenInput.isNotBlank() && !viewModel.isLoading
                    ) {
                        Text("Verificar")
                    }
                } else {
                    TextButton(
                        onClick = { showTokenInput = true },
                        enabled = !viewModel.isLoading
                    ) {
                        Text("¿Tienes un código manual?")
                    }
                }

                if (viewModel.errorMessage != null) {
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                if (viewModel.successMessage != null) {
                    Text(
                        text = viewModel.successMessage!!,
                        color = androidx.compose.ui.graphics.Color(0xFF0F3D37),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (email == null) {
                        OutlinedTextField(
                            value = viewModel.emailInput,
                            onValueChange = { viewModel.emailInput = it },
                            label = { Text("Correo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (viewModel.emailInput.isNotBlank()) {
                                    viewModel.resend(viewModel.emailInput.trim())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isLoading && viewModel.emailInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Reenviar correo")
                    }

                    TextButton(
                        onClick = {
                            viewModel.reset()
                            onBackToLogin()
                        },
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

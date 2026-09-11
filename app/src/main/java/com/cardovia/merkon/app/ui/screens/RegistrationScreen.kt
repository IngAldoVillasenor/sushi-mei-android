package com.cardovia.merkon.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardovia.merkon.app.data.api.NetworkModule
import com.cardovia.merkon.app.data.model.RegistrationRequestDto
import com.cardovia.merkon.app.data.api.ApiException
import kotlinx.coroutines.launch
import java.text.Normalizer

class RegistrationViewModel(
    private val api: com.cardovia.merkon.app.data.api.PublicMerkonApi = NetworkModule.publicMerkonApi
) : androidx.lifecycle.ViewModel() {
    var email by mutableStateOf("")
    var displayName by mutableStateOf("")
    var businessName by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var termsAccepted by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    val isEmailValid: Boolean
        get() = email.isNotBlank() && email.trim().length <= 320 && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    val isPasswordValid: Boolean
        get() {
            if (password.isEmpty()) return false
            val length = password.codePointCount(0, password.length)
            if (length !in 15..128) return false
            
            val normPass = Normalizer.normalize(password, Normalizer.Form.NFKC).lowercase()
            val normEmail = Normalizer.normalize(email.trim(), Normalizer.Form.NFKC).lowercase()
            
            // Check for obvious patterns
            if (normEmail.isNotBlank() && normPass.contains(normEmail)) return false
            if (normPass.contains("merkon") || normPass.contains("sushimei") || normPass.contains("sushi mei")) return false
            
            return true
        }

    val isFormValid: Boolean
        get() = isEmailValid &&
                displayName.isNotBlank() && displayName.trim().length <= 120 &&
                businessName.isNotBlank() && businessName.trim().length <= 160 &&
                isPasswordValid &&
                password == confirmPassword &&
                termsAccepted &&
                !isLoading

    fun clearSecrets() {
        password = ""
        confirmPassword = ""
    }

    suspend fun register(): Boolean {
        isLoading = true
        errorMessage = null
        try {
            val response = api.register(
                RegistrationRequestDto(
                    email = email.trim(),
                    displayName = displayName.trim(),
                    password = password,
                    businessName = businessName.trim(),
                    termsAccepted = termsAccepted
                )
            )
            
            if (response.isSuccessful && response.code() == 202) {
                clearSecrets()
                return true
            } else {
                errorMessage = "Error de validación o del servidor."
                return false
            }
        } catch (e: ApiException) {
            errorMessage = when {
                e.code == "AUTH_PASSWORD_REJECTED" -> "La contraseña no cumple con los requisitos de seguridad."
                e.code == "REGISTRATION_INVALID_REQUEST" -> "La solicitud de registro no es válida."
                e.httpStatus == 429 -> "Demasiados intentos. Por favor, espera antes de intentar de nuevo."
                else -> "Error de validación del registro."
            }
            return false
        } catch (e: Exception) {
            errorMessage = "Error de conexión."
            return false
        } finally {
            isLoading = false
        }
    }
}

@Composable
fun RegistrationScreen(
    onBack: () -> Unit,
    onRegistrationSuccess: (String) -> Unit,
    viewModel: RegistrationViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
                    text = "Crear mi negocio",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    isError = viewModel.email.isNotBlank() && !viewModel.isEmailValid,
                    enabled = !viewModel.isLoading
                )

                OutlinedTextField(
                    value = viewModel.displayName,
                    onValueChange = { viewModel.displayName = it },
                    label = { Text("Nombre de contacto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    enabled = !viewModel.isLoading
                )

                OutlinedTextField(
                    value = viewModel.businessName,
                    onValueChange = { viewModel.businessName = it },
                    label = { Text("Nombre del negocio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    enabled = !viewModel.isLoading
                )

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("Contraseña (mínimo 15 caracteres)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    isError = viewModel.password.isNotEmpty() && !viewModel.isPasswordValid,
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
                    isError = viewModel.confirmPassword.isNotEmpty() && viewModel.password != viewModel.confirmPassword,
                    enabled = !viewModel.isLoading
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.termsAccepted,
                        onCheckedChange = { viewModel.termsAccepted = it },
                        enabled = !viewModel.isLoading
                    )
                    Text(
                        text = "Acepto los Términos y Condiciones",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (viewModel.errorMessage != null) {
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        keyboardController?.hide()
                        coroutineScope.launch {
                            val success = viewModel.register()
                            if (success) {
                                onRegistrationSuccess(viewModel.email.trim())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.isFormValid
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Registrar negocio")
                    }
                }

                TextButton(
                    onClick = {
                        viewModel.clearSecrets()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isLoading
                ) {
                    Text("Volver a inicio de sesión")
                }
            }
        }
    }
}

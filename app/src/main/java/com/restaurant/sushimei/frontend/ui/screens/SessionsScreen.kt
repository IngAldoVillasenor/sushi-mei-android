package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.api.NetworkModule
import com.restaurant.sushimei.frontend.data.model.SessionDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadSessions() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val api = NetworkModule.sushiMeiApi
                val response = api.getSessions()
                if (response.isSuccessful && response.body() != null) {
                    sessions = response.body()!!
                } else {
                    errorMessage = "Error al cargar las sesiones."
                }
            } catch (e: Exception) {
                errorMessage = "Error de red o servicio no disponible."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Sesiones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { loadSessions() }) {
                        Text("Reintentar")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { session ->
                        SessionItem(
                            session = session,
                            onRevoke = {
                                coroutineScope.launch {
                                    try {
                                        NetworkModule.sushiMeiApi.revokeSession(session.id)
                                        loadSessions()
                                    } catch (e: Exception) {
                                        // Mute the error or show a snackbar
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(session: SessionDto, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (session.deviceName?.contains("Android", ignoreCase = true) == true) Icons.Default.Smartphone else Icons.Default.Computer,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.deviceName ?: "Dispositivo Desconocido",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "App: ${session.appVersion ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Creada: ${session.createdAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (session.current) {
                    Text(
                        text = "Sesión Actual",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (!session.current) {
                IconButton(onClick = onRevoke) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Revocar sesión",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

package com.restaurant.sushimei.frontend.ui.admin.tags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.CatalogTagDto
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTagsScreen(
    viewModel: AdminTagsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<CatalogTagDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←") // Or Icons.Default.ArrowBack if imported
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        editingTag = null
                        showDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Nuevo Tag")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.tags) { tag ->
                        TagRow(tag = tag, onEdit = {
                            editingTag = tag
                            showDialog = true
                        })
                    }
                }
            }
        }

        if (showDialog) {
            TagEditDialog(
                tag = editingTag,
                onDismiss = { showDialog = false },
                onSave = { newTag ->
                    viewModel.saveTag(newTag)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun TagRow(tag: CatalogTagDto, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(tag.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Código: ${tag.code}", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
        }
    }
}

@Composable
fun TagEditDialog(
    tag: CatalogTagDto?,
    onDismiss: () -> Unit,
    onSave: (CatalogTagDto) -> Unit
) {
    var name by remember { mutableStateOf(tag?.name ?: "") }
    var code by remember { mutableStateOf(tag?.code ?: "") }
    var active by remember { mutableStateOf(tag?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "Nuevo Tag" else "Editar Tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Tag") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val newTag = CatalogTagDto(
                    id = tag?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    code = code.takeIf { it.isNotBlank() } ?: name.uppercase(),
                    active = active,
                    displayOrder = tag?.displayOrder ?: 0,
                    version = tag?.version ?: 1L
                )
                onSave(newTag)
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

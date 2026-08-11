package com.restaurant.sushimei.frontend.ui.admin.configurator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationBuilderScreen(
    menuItemId: Long,
    viewModel: ConfigurationBuilderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showGroupDialog by remember { mutableStateOf(false) }
    var optionDialogForGroup by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(menuItemId) {
        viewModel.loadConfiguration(menuItemId)
    }

    if (uiState.isSaved) {
        LaunchedEffect(Unit) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration Builder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    Button(onClick = { viewModel.saveConfiguration() }) {
                        Text("Guardar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showGroupDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Grupo")
            }
        }
    ) { padding ->
        val config = uiState.configuration
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (config != null) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Propiedades Base", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Producto ID: ${config.menuItemId}")
                            Text("Nombre: ${config.name}")
                            Text("Precio Base: $${config.basePrice}")
                            Text("Independiente: ${if(config.standaloneOrderable) "Sí" else "No"}")
                            Text("Requiere Config: ${if(config.requiresConfiguration) "Sí" else "No"}")
                            // We could add editable fields here for base properties.
                        }
                    }
                }
                
                items(config.groups) { group ->
                    BuilderGroupCard(
                        group = group,
                        onDeleteGroup = { viewModel.removeGroup(group.id) },
                        onAddOption = { optionDialogForGroup = group.id },
                        onDeleteOption = { optionId -> viewModel.removeOption(group.id, optionId) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // space for FAB
                }
            }
        }
        
        if (showGroupDialog) {
            AddGroupDialog(
                onDismiss = { showGroupDialog = false },
                onAdd = { name, min, max, allowDups ->
                    viewModel.addGroup(name, min, max, allowDups)
                    showGroupDialog = false
                }
            )
        }
        
        optionDialogForGroup?.let { groupId ->
            AddOptionDialog(
                onDismiss = { optionDialogForGroup = null },
                onAdd = { name, targetId, priceAdj ->
                    viewModel.addOption(groupId, name, targetId, priceAdj)
                    optionDialogForGroup = null
                }
            )
        }
    }
}

@Composable
fun BuilderGroupCard(
    group: ConfigurationGroupDto,
    onDeleteGroup: () -> Unit,
    onAddOption: () -> Unit,
    onDeleteOption: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDeleteGroup) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar Grupo", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text("Selecciones: Min ${group.minSelections} - Max ${group.maxSelections}", style = MaterialTheme.typography.bodySmall)
            Text("Permite Duplicados: ${if(group.allowDuplicates) "Sí" else "No"}", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            group.options.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(option.name, style = MaterialTheme.typography.bodyMedium)
                        Text(if (option.priceAdjustment > java.math.BigDecimal.ZERO) "+$${option.priceAdjustment}" else "Incluido", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDeleteOption(option.menuItemId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar Opción", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            TextButton(onClick = onAddOption, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("+ Agregar Opción")
            }
        }
    }
}

@Composable
fun AddGroupDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, Int, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var minStr by remember { mutableStateOf("0") }
    var maxStr by remember { mutableStateOf("1") }
    var allowDups by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Grupo de Opciones") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del Grupo (Ej. Bebida, Extras)") })
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = minStr, onValueChange = { minStr = it }, label = { Text("Min") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxStr, onValueChange = { maxStr = it }, label = { Text("Max") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowDups, onCheckedChange = { allowDups = it })
                    Text("Permitir cantidades > 1 por opción")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val min = minStr.toIntOrNull() ?: 0
                val max = maxStr.toIntOrNull() ?: 1
                onAdd(name, min, max, allowDups)
            }) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddOptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Long?, java.math.BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var priceAdjStr by remember { mutableStateOf("0.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Opción") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre de la Opción") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = targetId, onValueChange = { targetId = it }, label = { Text("Target MenuItemId (Opcional)") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = priceAdjStr, onValueChange = { priceAdjStr = it }, label = { Text("Ajuste de Precio (+)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val priceAdj = priceAdjStr.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                val targetLong = targetId.takeIf { it.isNotBlank() }?.toLongOrNull()
                onAdd(name, targetLong, priceAdj)
            }) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

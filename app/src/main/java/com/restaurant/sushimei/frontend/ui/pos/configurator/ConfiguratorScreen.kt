package com.restaurant.sushimei.frontend.ui.pos.configurator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguratorScreen(
    menuItemId: String,
    viewModel: ConfiguratorViewModel,
    onDismiss: () -> Unit,
    onAddToCart: (com.restaurant.sushimei.frontend.data.model.ConfiguredProduct) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(menuItemId) {
        viewModel.loadConfiguration(menuItemId)
    }

    if (uiState.isLoadingConfig) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val config = uiState.configuration
    if (config == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: No se pudo cargar la configuración")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config.name) },
                actions = {
                    Text(
                        text = "$${config.basePrice}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                val canSubmit = uiState.quoteState == QuoteState.VALID
                val total = uiState.latestQuote?.total ?: config.basePrice

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (uiState.quoteState == QuoteState.LOADING) {
                            Text("Cotizando...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Total: $$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Button(
                        onClick = {
                            uiState.latestQuote?.let { quote ->
                                // Mapping QuoteResponseDto to ConfiguredProduct domain model
                                val product = com.restaurant.sushimei.frontend.data.model.ConfiguredProduct(
                                    menuItemId = quote.menuItemId,
                                    name = quote.name,
                                    quantity = quote.quantity,
                                    baseUnitPrice = quote.baseUnitPrice,
                                    unitTotal = quote.unitTotal,
                                    total = quote.total,
                                    groups = quote.groups.map { groupDto ->
                                        com.restaurant.sushimei.frontend.data.model.ConfiguredGroup(
                                            groupId = groupDto.groupId,
                                            name = groupDto.name,
                                            selections = groupDto.selections.map { selDto ->
                                                com.restaurant.sushimei.frontend.data.model.ConfiguredSelection(
                                                    menuItemId = selDto.menuItemId,
                                                    name = selDto.name,
                                                    quantity = selDto.quantity,
                                                    catalogUnitPrice = selDto.catalogUnitPrice,
                                                    priceAdjustment = selDto.priceAdjustment
                                                )
                                            }
                                        )
                                    }
                                )
                                onAddToCart(product)
                            }
                        },
                        enabled = canSubmit
                    ) {
                        Text("Agregar a pedido")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(config.groups) { group ->
                val selections = uiState.selections[group.id] ?: emptyList()
                ConfigurationGroupView(
                    group = group,
                    currentSelections = selections,
                    onAdd = { option -> viewModel.addSelection(group.id, option) },
                    onRemove = { option -> viewModel.removeSelection(group.id, option) }
                )
            }
        }
    }
}

@Composable
fun ConfigurationGroupView(
    group: ConfigurationGroupDto,
    currentSelections: List<ConfigurationOptionDto>,
    onAdd: (ConfigurationOptionDto) -> Unit,
    onRemove: (ConfigurationOptionDto) -> Unit
) {
    val totalSelected = currentSelections.size
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                val reqText = if (group.minSelections == group.maxSelections) {
                    "$totalSelected / ${group.maxSelections}"
                } else if (group.minSelections == 0) {
                    "Opcional (Max ${group.maxSelections})"
                } else {
                    "$totalSelected / ${group.minSelections}-${group.maxSelections}"
                }
                
                Text(
                    text = reqText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (totalSelected in group.minSelections..group.maxSelections) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            group.options.forEach { option ->
                val optionCount = currentSelections.count { it.menuItemId == option.menuItemId }
                ConfigurationOptionRow(
                    option = option,
                    quantity = optionCount,
                    allowDuplicates = group.allowDuplicates,
                    canAddMore = totalSelected < group.maxSelections,
                    onAdd = { onAdd(option) },
                    onRemove = { onRemove(option) }
                )
            }
        }
    }
}

@Composable
fun ConfigurationOptionRow(
    option: ConfigurationOptionDto,
    quantity: Int,
    allowDuplicates: Boolean,
    canAddMore: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (option.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            val priceStr = if (option.priceAdjustment == 0.0) "Incluido" else "+$${option.priceAdjustment}"
            Text(
                text = priceStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!option.available) {
                Text(
                    text = "Agotado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        if (allowDuplicates) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove, enabled = quantity > 0) {
                    Icon(Icons.Default.Remove, contentDescription = "Menos")
                }
                Text("$quantity", modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = onAdd, enabled = canAddMore && option.available) {
                    Icon(Icons.Default.Add, contentDescription = "Más")
                }
            }
        } else {
            val isSelected = quantity > 0
            FilledTonalIconToggleButton(
                checked = isSelected,
                onCheckedChange = { checked ->
                    if (checked) onAdd() else onRemove()
                },
                enabled = option.available && (isSelected || canAddMore)
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = "Seleccionado")
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Seleccionar")
                }
            }
        }
    }
}

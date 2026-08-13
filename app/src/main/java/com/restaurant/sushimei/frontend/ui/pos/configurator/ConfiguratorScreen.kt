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
import com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseGroupDto

fun ItemQuoteResponseGroupDto.toDomain(): com.restaurant.sushimei.frontend.data.model.ConfiguredGroup {
    return com.restaurant.sushimei.frontend.data.model.ConfiguredGroup(
        groupId = this.groupId,
        name = this.name,
        selections = this.selections.map { selDto ->
            com.restaurant.sushimei.frontend.data.model.ConfiguredSelection(
                menuItemId = selDto.menuItemId,
                name = selDto.name,
                quantity = selDto.quantity,
                catalogUnitPrice = selDto.catalogUnitPrice,
                priceAdjustment = selDto.priceAdjustment,
                groups = selDto.groups.map { it.toDomain() }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguratorScreen(
    menuItemId: Long,
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
                                val product = com.restaurant.sushimei.frontend.data.model.ConfiguredProduct(
                                    menuItemId = quote.menuItemId,
                                    name = quote.name,
                                    quantity = quote.quantity,
                                    baseUnitPrice = quote.baseUnitPrice,
                                    unitTotal = quote.unitTotal,
                                    total = quote.total,
                                    groups = quote.groups.map { it.toDomain() }
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
            items(config.groups, key = { it.id }) { group ->
                val selections = uiState.rootSelections[group.id] ?: emptyList()
                ConfigurationGroupView(
                    group = group,
                    currentSelections = selections,
                    onAdd = { childGroupId, option, parentOccurrenceId ->
                        viewModel.addSelection(childGroupId, option, parentOccurrenceId)
                    },
                    onRemove = { occurrenceId ->
                        viewModel.removeSelection(occurrenceId)
                    }
                )
            }
        }
    }
}

@Composable
fun ConfigurationGroupView(
    group: ConfigurationGroupDto,
    currentSelections: List<SelectionNode>,
    onAdd: (groupId: Long, option: ConfigurationOptionDto, parentOccurrenceId: String?) -> Unit,
    onRemove: (occurrenceId: String) -> Unit,
    parentOccurrenceId: String? = null
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
                val optionNodes = currentSelections.filter { it.option.menuItemId == option.menuItemId }
                val quantity = optionNodes.size

                ConfigurationOptionRow(
                    option = option,
                    quantity = quantity,
                    allowDuplicates = group.allowDuplicates,
                    canAddMore = totalSelected < group.maxSelections,
                    onAdd = { onAdd(group.id, option, parentOccurrenceId) },
                    onRemove = {
                        if (optionNodes.isNotEmpty()) {
                            onRemove(optionNodes.last().occurrenceId)
                        }
                    }
                )

                optionNodes.forEachIndexed { index, node ->
                    if (node.option.requiresConfiguration) {
                        Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)) {
                            if (group.allowDuplicates && quantity > 1) {
                                Text("Ocurrencia ${index + 1}", style = MaterialTheme.typography.labelSmall)
                            }

                            if (node.isLoadingNested) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else if (node.nestedError != null) {
                                Text(node.nestedError, color = MaterialTheme.colorScheme.error)
                            } else if (node.nestedConfiguration != null) {
                                node.nestedConfiguration.groups.forEach { childGroup ->
                                    val childSelections = node.nestedSelections[childGroup.id] ?: emptyList()
                                    ConfigurationGroupView(
                                        group = childGroup,
                                        currentSelections = childSelections,
                                        onAdd = onAdd,
                                        onRemove = onRemove,
                                        parentOccurrenceId = node.occurrenceId
                                    )
                                }
                            }
                        }
                    }
                }
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
            if (option.priceAdjustment.compareTo(java.math.BigDecimal.ZERO) > 0) {
                Text(
                    text = "+$${option.priceAdjustment}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!option.available) {
            Text("No disponible", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            return@Row
        }

        if (allowDuplicates) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove, enabled = quantity > 0) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove")
                }
                Text(text = quantity.toString(), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = onAdd, enabled = canAddMore) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        } else {
            val isSelected = quantity > 0
            if (isSelected) {
                FilledTonalIconButton(onClick = onRemove) {
                    Icon(Icons.Default.Check, contentDescription = "Selected")
                }
            } else {
                IconButton(onClick = onAdd, enabled = canAddMore) {
                    Icon(Icons.Default.Add, contentDescription = "Select")
                }
            }
        }
    }
}

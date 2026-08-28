package com.restaurant.sushimei.frontend.ui.admin.configurator

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.PricingPolicy
import com.restaurant.sushimei.frontend.data.model.SelectionRuleTargetType
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationBuilderScreen(
    menuItemId: Long,
    viewModel: ConfigurationBuilderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var showGroupDialog by remember { mutableStateOf<DraftGroup?>(null) }
    var isCreatingGroup by remember { mutableStateOf(false) }

    var showRuleDialogForGroup by remember { mutableStateOf<String?>(null) }
    var editingRule by remember { mutableStateOf<DraftRule?>(null) }

    val context = LocalContext.current

    LaunchedEffect(menuItemId) {
        viewModel.loadConfiguration(menuItemId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Configuración guardada exitosamente", Toast.LENGTH_SHORT).show()
            viewModel.resetSaved()
            onBack()
        }
    }


    val isLoaded = uiState.originalDefinition != null && !uiState.isLoading
    val canEdit = isLoaded && !uiState.isSaving && uiState.originalDefinition?.menuItemId == menuItemId

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(uiState.errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                if (uiState.originalDefinition == null && !uiState.isSaving) {
                    TextButton(onClick = {
                        viewModel.dismissError()
                        viewModel.loadConfiguration(menuItemId)
                    }) {
                        Text("Reintentar")
                    }
                }
            }
        )
    }

    if (showGroupDialog != null || isCreatingGroup) {
        val group = showGroupDialog
        var name by remember { mutableStateOf(group?.name ?: "") }
        var minSel by remember { mutableStateOf(group?.minSelections?.toString() ?: "0") }
        var maxSel by remember { mutableStateOf(group?.maxSelections?.toString() ?: "1") }
        var displayOrder by remember { mutableStateOf(group?.displayOrder?.toString() ?: "0") }
        var allowDup by remember { mutableStateOf(group?.allowDuplicates ?: false) }

        AlertDialog(
            onDismissRequest = {
                showGroupDialog = null
                isCreatingGroup = false
            },
            title = { Text(if (isCreatingGroup) "Nuevo Grupo" else "Editar Grupo") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
                    OutlinedTextField(value = minSel, onValueChange = { minSel = it }, label = { Text("Mínimo Selecciones") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = maxSel, onValueChange = { maxSel = it }, label = { Text("Máximo Selecciones") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = displayOrder, onValueChange = { displayOrder = it }, label = { Text("Orden (Display)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowDup, onCheckedChange = { allowDup = it })
                        Text("Permitir Duplicados")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val min = minSel.toIntOrNull() ?: 0
                    val max = maxSel.toIntOrNull() ?: 1
                    val order = displayOrder.toIntOrNull() ?: 0
                    if (isCreatingGroup) {
                        viewModel.addGroup(name, min, max, allowDup, order)
                    } else if (group != null) {
                        viewModel.updateGroup(group.localId, name, min, max, allowDup, order)
                    }
                    showGroupDialog = null
                    isCreatingGroup = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGroupDialog = null
                    isCreatingGroup = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showRuleDialogForGroup != null) {
        val isCreatingRule = editingRule == null
        val rule = editingRule

        var targetType by remember { mutableStateOf(rule?.targetType ?: SelectionRuleTargetType.ITEM) }
        var targetId by remember { mutableStateOf(rule?.targetId) }
        var pricingPolicy by remember { mutableStateOf(rule?.pricingPolicy ?: PricingPolicy.INCLUDED) }
        var referencePriceStr by remember { mutableStateOf(rule?.referencePrice?.toString() ?: "") }
        var fixedSurchargeStr by remember { mutableStateOf(rule?.fixedSurcharge?.toString() ?: "") }
        var priority by remember { mutableStateOf(rule?.priority?.toString() ?: "0") }

        var expandedTargetType by remember { mutableStateOf(false) }
        var expandedPricing by remember { mutableStateOf(false) }
        var expandedTargetSelection by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showRuleDialogForGroup = null
                editingRule = null
            },
            title = { Text(if (isCreatingRule) "Nueva Regla" else "Editar Regla") },
            text = {
                Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    ExposedDropdownMenuBox(
                        expanded = expandedTargetType,
                        onExpandedChange = { expandedTargetType = it }
                    ) {
                        OutlinedTextField(
                            value = targetType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de Target") },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTargetType,
                            onDismissRequest = { expandedTargetType = false }
                        ) {
                            SelectionRuleTargetType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        targetType = type
                                        targetId = null
                                        expandedTargetType = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val selectedItemName = if (targetType == SelectionRuleTargetType.ITEM) {
                        uiState.availableItems.find { it.id == targetId }?.nombre ?: "Seleccionar Ítem"
                    } else {
                        uiState.availableTags.find { it.id == targetId }?.name ?: "Seleccionar Tag"
                    }

                    ExposedDropdownMenuBox(
                        expanded = expandedTargetSelection,
                        onExpandedChange = { expandedTargetSelection = it }
                    ) {
                        OutlinedTextField(
                            value = selectedItemName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target") },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTargetSelection,
                            onDismissRequest = { expandedTargetSelection = false }
                        ) {
                            if (targetType == SelectionRuleTargetType.ITEM) {
                                uiState.availableItems.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text("${item.id}: ${item.nombre}") },
                                        onClick = {
                                            targetId = item.id
                                            expandedTargetSelection = false
                                        }
                                    )
                                }
                            } else {
                                uiState.availableTags.forEach { tag ->
                                    DropdownMenuItem(
                                        text = { Text("${tag.id}: ${tag.name}") },
                                        onClick = {
                                            targetId = tag.id
                                            expandedTargetSelection = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = expandedPricing,
                        onExpandedChange = { expandedPricing = it }
                    ) {
                        OutlinedTextField(
                            value = pricingPolicy.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Política de Precio") },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPricing,
                            onDismissRequest = { expandedPricing = false }
                        ) {
                            PricingPolicy.values().forEach { policy ->
                                DropdownMenuItem(
                                    text = { Text(policy.name) },
                                    onClick = {
                                        pricingPolicy = policy
                                        expandedPricing = false
                                    }
                                )
                            }
                        }
                    }

                    if (pricingPolicy == PricingPolicy.PRICE_DIFFERENCE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = referencePriceStr,
                            onValueChange = { referencePriceStr = it },
                            label = { Text("Precio de Referencia") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    } else if (pricingPolicy == PricingPolicy.FIXED_SURCHARGE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = fixedSurchargeStr,
                            onValueChange = { fixedSurchargeStr = it },
                            label = { Text("Cargo Fijo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it },
                        label = { Text("Prioridad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tId = targetId
                    if (tId == null) {
                        Toast.makeText(context, "Debe seleccionar un target", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val refPrice = if (pricingPolicy == PricingPolicy.PRICE_DIFFERENCE) referencePriceStr.toBigDecimalOrNull() else null
                    val fixSur = if (pricingPolicy == PricingPolicy.FIXED_SURCHARGE) fixedSurchargeStr.toBigDecimalOrNull() else null
                    val prio = priority.toIntOrNull() ?: 0

                    if (isCreatingRule) {
                        viewModel.addRule(showRuleDialogForGroup!!, targetType, tId, pricingPolicy, refPrice, fixSur, prio)
                    } else {
                        viewModel.updateRule(showRuleDialogForGroup!!, rule!!.localId, targetType, tId, pricingPolicy, refPrice, fixSur, prio)
                    }
                    showRuleDialogForGroup = null
                    editingRule = null
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRuleDialogForGroup = null
                    editingRule = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración del producto") },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (canEdit) {
                        TextButton(onClick = { viewModel.saveConfiguration() }) {
                            Text("Guardar")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { isCreatingGroup = true }) {
                    Icon(Icons.Filled.Add, "Agregar Grupo")
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                items(uiState.draftGroups) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Row {
                                    if (canEdit) {
                                        IconButton(onClick = { showGroupDialog = group }) {
                                            Icon(Icons.Filled.Edit, "Editar Grupo")
                                        }
                                    }
                                    if (canEdit) {
                                        IconButton(onClick = { viewModel.removeGroup(group.localId) }) {
                                            Icon(Icons.Filled.Delete, "Eliminar Grupo")
                                        }
                                    }
                                }
                            }
                            Text("Min: ${group.minSelections} - Max: ${group.maxSelections} - Dup: ${group.allowDuplicates}")
                            Text("Orden: ${group.displayOrder}")

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))

                            group.rules.forEach { rule ->
                                val targetName = if (rule.targetType == SelectionRuleTargetType.ITEM) {
                                    uiState.availableItems.find { it.id == rule.targetId }?.nombre ?: rule.targetId.toString()
                                } else {
                                    uiState.availableTags.find { it.id == rule.targetId }?.name ?: rule.targetId.toString()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${rule.targetType}: $targetName", fontWeight = FontWeight.SemiBold)
                                        Text("${rule.pricingPolicy} | Pri: ${rule.priority}")
                                        if (rule.pricingPolicy == PricingPolicy.PRICE_DIFFERENCE) {
                                            Text("Ref: ${rule.referencePrice}")
                                        }
                                        if (rule.pricingPolicy == PricingPolicy.FIXED_SURCHARGE) {
                                            Text("Fijo: ${rule.fixedSurcharge}")
                                        }
                                    }
                                    Row {
                                        if (canEdit) {
                                            IconButton(onClick = {
                                                showRuleDialogForGroup = group.localId
                                                editingRule = rule
                                            }) {
                                                Icon(Icons.Filled.Edit, "Editar Regla")
                                            }
                                        }
                                        if (canEdit) {
                                            IconButton(onClick = { viewModel.removeRule(group.localId, rule.localId) }) {
                                                Icon(Icons.Filled.Delete, "Eliminar Regla")
                                            }
                                        }
                                    }
                                }
                            }
                            if (canEdit) {
                                TextButton(onClick = {
                                    showRuleDialogForGroup = group.localId
                                    editingRule = null
                                }) {
                                    Text("+ Añadir Regla")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

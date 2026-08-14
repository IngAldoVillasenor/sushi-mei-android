package com.restaurant.sushimei.frontend.ui.admin.promotions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionEditorScreen(
    promotion: Promotion?,
    viewModel: PromotionsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf(promotion?.name ?: "") }
    var active by remember { mutableStateOf(promotion?.active ?: true) }
    
    val targets = remember(promotion?.id, promotion?.version) {
        mutableStateListOf<PromotionTarget>().apply {
            addAll(promotion?.targets.orEmpty())
        }
    }
    var targetType by remember { mutableStateOf(PromotionTargetType.TAG) }
    var targetId by remember { mutableStateOf("") }
    var targetDisplayName by remember { mutableStateOf("") }
    
    // Schedule
    var daysOfWeek by remember { mutableStateOf(promotion?.schedule?.daysOfWeek ?: emptySet()) }
    var allDay by remember { mutableStateOf(promotion?.schedule?.allDay ?: true) }
    
    // Benefit
    var benefitType by remember { 
        mutableStateOf(
            when (promotion?.benefit) {
                is PromotionBenefit.FixedUnitPrice -> "FIXED_UNIT_PRICE"
                is PromotionBenefit.BuyXGetYSameItem -> "BUY_X_GET_Y_SAME_ITEM"
                null -> "FIXED_UNIT_PRICE"
            }
        ) 
    }
    
    // Fixed Unit Price fields
    var fixedPrice by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.FixedUnitPrice)?.amount?.toString() ?: ""
        ) 
    }
    
    // Buy X Get Y Same Item fields
    var buyQuantity by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.BuyXGetYSameItem)?.buyQuantity?.toString() ?: "2"
        ) 
    }
    var rewardQuantity by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.BuyXGetYSameItem)?.rewardQuantity?.toString() ?: "1"
        ) 
    }
    var repeat by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.BuyXGetYSameItem)?.repeat ?: true
        )
    }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.acknowledgeSaveSuccess()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (promotion == null) "Nueva Promoción" else "Editar Promoción") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val displayedError = formError ?: uiState.errorMessage
            if (displayedError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(displayedError, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la Promoción") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = active, onCheckedChange = { active = it })
                Spacer(Modifier.width(8.dp))
                Text("Promoción Activa", style = MaterialTheme.typography.bodyLarge)
            }
            
            HorizontalDivider()
            
            Text("Objetivos de la promoción", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (targets.isEmpty()) {
                Text(
                    "Agrega al menos una etiqueta o producto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                targets.forEachIndexed { index, target ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (target.type == PromotionTargetType.TAG) "Etiqueta #${target.targetId}"
                                    else "Producto #${target.targetId}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (target.displayName.isNotBlank() && target.displayName != target.targetId.toString()) {
                                    Text(target.displayName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { targets.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar objetivo")
                            }
                        }
                    }
                }
            }

            Text("Agregar objetivo", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = targetType == PromotionTargetType.TAG, onClick = { targetType = PromotionTargetType.TAG })
                    Text("Etiqueta (Tag)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = targetType == PromotionTargetType.ITEM, onClick = { targetType = PromotionTargetType.ITEM })
                    Text("Producto Específico")
                }
            }
            OutlinedTextField(
                value = targetId,
                onValueChange = { targetId = it },
                label = { Text("ID numérico del nuevo objetivo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetDisplayName,
                onValueChange = { targetDisplayName = it },
                label = { Text("Nombre a Mostrar (ej. Rollos Clásicos)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    val parsedTargetId = targetId.toLongOrNull()
                    formError = when {
                        parsedTargetId == null || parsedTargetId <= 0L ->
                            "Escribe un ID válido para el nuevo objetivo."
                        targets.any { it.type == targetType && it.targetId == parsedTargetId } ->
                            "Ese objetivo ya está agregado."
                        else -> null
                    }
                    if (formError == null) {
                        targets.add(
                            PromotionTarget(
                                type = targetType,
                                targetId = requireNotNull(parsedTargetId),
                                displayName = targetDisplayName.trim().ifBlank { parsedTargetId.toString() }
                            )
                        )
                        targetId = ""
                        targetDisplayName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar objetivo")
            }

            HorizontalDivider()
            
            Text("Días de la semana", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val days = listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
                days.forEach { (num, label) ->
                    val isSelected = daysOfWeek.contains(num)
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            daysOfWeek = if (isSelected) daysOfWeek - num else daysOfWeek + num 
                        },
                        label = { Text(label) }
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                Text("Todo el día")
            }
            
            HorizontalDivider()
            
            Text("Beneficio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = benefitType == "FIXED_UNIT_PRICE", 
                        onClick = { 
                            benefitType = "FIXED_UNIT_PRICE"
                            // Clear irrelevant fields
                            buyQuantity = ""
                            rewardQuantity = ""
                            repeat = true
                        }
                    )
                    Text("Precio Unitario Fijo")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = benefitType == "BUY_X_GET_Y_SAME_ITEM",
                        onClick = { 
                            benefitType = "BUY_X_GET_Y_SAME_ITEM" 
                            // Clear irrelevant fields
                            fixedPrice = ""
                        }
                    )
                    Text("Compra X / Regala Y")
                }
            }
            
            if (benefitType == "FIXED_UNIT_PRICE") {
                OutlinedTextField(
                    value = fixedPrice,
                    onValueChange = { fixedPrice = it },
                    label = { Text("Precio Promocional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = buyQuantity,
                        onValueChange = { buyQuantity = it },
                        label = { Text("Compra (Cant)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rewardQuantity,
                        onValueChange = { rewardQuantity = it },
                        label = { Text("Regala (Cant)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = repeat, onCheckedChange = { repeat = it })
                    Text("Repetir por cada grupo completo (ej. 2x1, 4x2)")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val normalizedName = name.trim()
                    val parsedFixedPrice = fixedPrice.toBigDecimalOrNull()
                    val parsedBuyQuantity = buyQuantity.toIntOrNull()
                    val parsedRewardQuantity = rewardQuantity.toIntOrNull()
                    formError = when {
                        normalizedName.isEmpty() -> "Escribe un nombre para la promoción."
                        daysOfWeek.isEmpty() -> "Selecciona al menos un día de la semana."
                        targets.isEmpty() -> "Agrega al menos una etiqueta o producto."
                        benefitType == "FIXED_UNIT_PRICE" &&
                            (parsedFixedPrice == null || parsedFixedPrice <= java.math.BigDecimal.ZERO) ->
                            "El precio promocional debe ser mayor a cero."
                        benefitType == "BUY_X_GET_Y_SAME_ITEM" &&
                            (parsedBuyQuantity == null || parsedBuyQuantity <= 0 ||
                                parsedRewardQuantity == null || parsedRewardQuantity <= 0) ->
                            "Las cantidades de compra y regalo deben ser mayores a cero."
                        else -> null
                    }
                    if (formError != null) {
                        return@Button
                    }

                    val benefit = if (benefitType == "FIXED_UNIT_PRICE") {
                        PromotionBenefit.FixedUnitPrice(requireNotNull(parsedFixedPrice))
                    } else {
                        PromotionBenefit.BuyXGetYSameItem(
                            buyQuantity = requireNotNull(parsedBuyQuantity),
                            rewardQuantity = requireNotNull(parsedRewardQuantity),
                            repeat = repeat
                        )
                    }
                    
                    val updatedPromotion = buildPromotionFromEditor(
                        originalPromotion = promotion,
                        name = normalizedName,
                        active = active,
                        daysOfWeek = daysOfWeek,
                        allDay = allDay,
                        targets = targets.toList(),
                        benefit = benefit
                    )
                    viewModel.savePromotion(updatedPromotion)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text(if (promotion == null) "Crear Promoción" else "Guardar Cambios")
                }
            }
        }
    }
}

internal fun buildPromotionFromEditor(
    originalPromotion: Promotion?,
    name: String,
    active: Boolean,
    daysOfWeek: Set<Int>,
    allDay: Boolean,
    targets: List<PromotionTarget>,
    benefit: PromotionBenefit
) = Promotion(
    id = originalPromotion?.id ?: 0L,
    name = name,
    active = active,
    priority = originalPromotion?.priority ?: 100,
    validFrom = originalPromotion?.validFrom,
    validUntil = originalPromotion?.validUntil,
    schedule = PromotionSchedule(daysOfWeek = daysOfWeek, allDay = allDay),
    targets = targets,
    benefit = benefit,
    version = originalPromotion?.version ?: 1L
)

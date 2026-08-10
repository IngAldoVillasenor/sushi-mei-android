package com.restaurant.sushimei.frontend.ui.admin.promotions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    
    // Target
    var targetType by remember { mutableStateOf(promotion?.target?.type ?: PromotionTargetType.TAG) }
    var targetId by remember { mutableStateOf(promotion?.target?.targetId ?: "") }
    var targetDisplayName by remember { mutableStateOf(promotion?.target?.displayName ?: "") }
    
    // Schedule
    var daysOfWeek by remember { mutableStateOf(promotion?.schedule?.daysOfWeek ?: emptySet()) }
    var allDay by remember { mutableStateOf(promotion?.schedule?.allDay ?: true) }
    
    // Benefit
    var benefitType by remember { 
        mutableStateOf(
            when (promotion?.benefit) {
                is PromotionBenefit.FixedUnitPrice -> "FIXED_UNIT_PRICE"
                is PromotionBenefit.BuyXPayY -> "BUY_X_PAY_Y"
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
    
    // Buy X Pay Y fields
    var buyQuantity by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.BuyXPayY)?.buyQuantity?.toString() ?: "2"
        ) 
    }
    var payQuantity by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.BuyXPayY)?.payQuantity?.toString() ?: "1"
        ) 
    }
    var repeat by remember { 
        mutableStateOf(
            (promotion?.benefit as? PromotionBenefit.BuyXPayY)?.repeat ?: true
        ) 
    }

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
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer)
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
            
            Text("Objetivo (Target)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                label = { Text("ID del Target (ej. ROLL_CLASSIC)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetDisplayName,
                onValueChange = { targetDisplayName = it },
                label = { Text("Nombre a Mostrar (ej. Rollos Clásicos)") },
                modifier = Modifier.fillMaxWidth()
            )

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
                            payQuantity = ""
                            repeat = true
                        }
                    )
                    Text("Precio Unitario Fijo")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = benefitType == "BUY_X_PAY_Y", 
                        onClick = { 
                            benefitType = "BUY_X_PAY_Y" 
                            // Clear irrelevant fields
                            fixedPrice = ""
                        }
                    )
                    Text("Compra X / Paga Y")
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
                        value = payQuantity,
                        onValueChange = { payQuantity = it },
                        label = { Text("Paga (Cant)") },
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
                    val benefit = if (benefitType == "FIXED_UNIT_PRICE") {
                        PromotionBenefit.FixedUnitPrice(fixedPrice.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
                    } else {
                        PromotionBenefit.BuyXPayY(
                            buyQuantity = buyQuantity.toIntOrNull() ?: 2,
                            payQuantity = payQuantity.toIntOrNull() ?: 1,
                            repeat = repeat
                        )
                    }
                    
                    val updatedPromotion = Promotion(
                        id = promotion?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        active = active,
                        priority = promotion?.priority ?: 100,
                        schedule = PromotionSchedule(
                            daysOfWeek = daysOfWeek,
                            allDay = allDay
                        ),
                        target = PromotionTarget(
                            type = targetType,
                            targetId = targetId,
                            displayName = targetDisplayName
                        ),
                        benefit = benefit,
                        version = promotion?.version ?: 1L
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

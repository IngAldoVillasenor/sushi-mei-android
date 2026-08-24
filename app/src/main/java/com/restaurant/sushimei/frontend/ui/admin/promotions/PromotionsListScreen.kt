package com.restaurant.sushimei.frontend.ui.admin.promotions

import com.restaurant.sushimei.frontend.ui.util.formatCurrency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.Promotion
import com.restaurant.sushimei.frontend.data.model.PromotionBenefit
import com.restaurant.sushimei.frontend.data.model.PromotionTargetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsListScreen(
    viewModel: PromotionsViewModel,
    onBack: () -> Unit,
    onEditPromotion: (Promotion?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Promociones Temporales (ERP)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadPromotions) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar promociones")
                    }
                    IconButton(onClick = { onEditPromotion(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Nueva Promoción")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditPromotion(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Promoción")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(onClick = viewModel::loadPromotions) {
                            Text("Reintentar")
                        }
                    }
                }
            } else if (uiState.promotions.isEmpty()) {
                Text(
                    text = "No hay promociones registradas.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.promotions, key = { it.id }) { promotion ->
                        PromotionCard(
                            promotion = promotion,
                            onClick = { onEditPromotion(promotion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PromotionCard(
    promotion: Promotion,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (promotion.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = promotion.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (promotion.active) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                
                Badge(
                    containerColor = if (promotion.active) Color(0xFF2E7D32) else Color.Gray,
                    contentColor = Color.White
                ) {
                    Text(
                        text = if (promotion.active) "ACTIVA" else "INACTIVA",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val daysStr = promotion.schedule.daysOfWeek.joinToString(", ") { day ->
                when(day) {
                    1 -> "Lunes"
                    2 -> "Martes"
                    3 -> "Miércoles"
                    4 -> "Jueves"
                    5 -> "Viernes"
                    6 -> "Sábado"
                    7 -> "Domingo"
                    else -> ""
                }
            }
            Text(text = "Días: $daysStr", style = MaterialTheme.typography.bodySmall)
            val targetsText = promotion.targets.joinToString(", ") { target ->
                if (target.type == PromotionTargetType.TAG) "Etiqueta #${target.targetId}"
                else "Producto #${target.targetId}"
            }
            Text(text = "Objetivos: $targetsText", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
        when (val benefit = promotion.benefit) {
                is PromotionBenefit.FixedUnitPrice -> {
                    Text(text = "Beneficio: Precio fijo ${formatCurrency(benefit.amount)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                is PromotionBenefit.BuyXGetY -> {
                    Text(
                        text = "Beneficio: Compra ${benefit.buyQuantity}, Regala ${benefit.rewardQuantity} (Repetir: ${if(benefit.repeat) "Sí" else "No"})", 
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

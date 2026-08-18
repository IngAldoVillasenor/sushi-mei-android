package com.restaurant.sushimei.frontend.ui.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.Promotion
import com.restaurant.sushimei.frontend.data.model.PromotionBenefit
import java.util.Locale

/**
 * Single-screen BOGO picker for BUY_X_GET_Y_ELIGIBLE_ITEM promotions.
 *
 * The cashier picks all rolls (purchased + reward) from one dialog.
 * Pressing + ALWAYS fills the next slot immediately — MenuItem.requiresConfiguration
 * is deliberately ignored here because roll configuration is a standalone ordering
 * context, never a BOGO selection context.
 *
 * Selection order determines slot assignment:
 *   - Slot 1 -> purchased (badge "Pago")
 *   - Slots 2..N -> reward (badge "Gratis")
 *
 * When the required total count (buyQuantity + rewardQuantity) is reached,
 * onComplete is invoked immediately with no intermediate confirmation screen.
 */
@Composable
fun FlexibleBogoPickerDialog(
    promotion: Promotion,
    eligibleProducts: List<MenuItem>,
    onDismiss: () -> Unit,
    onComplete: (purchasedMenuItem: MenuItem, rewardMenuItems: List<MenuItem>) -> Unit
) {
    val bogo = promotion.benefit as? PromotionBenefit.BuyXGetY ?: return
    // The cart line model represents ONE purchased product with quantity = buyQuantity.
    // Thus the picker needs 1 slot for the purchased product, and rewardQuantity slots for rewards.
    val totalSlots = 1 + bogo.rewardQuantity

    // Ordered list of selections -- index 0 = purchased, 1..N = rewards.
    // Invariant: size <= totalSlots at all times.
    var slots by remember { mutableStateOf<List<MenuItem>>(emptyList()) }

    // Derived counts per product (for the +/- display)
    val countByProductId = remember(slots) {
        slots.groupingBy { it.id }.eachCount()
    }

    // Auto-complete when all slots are filled.
    LaunchedEffect(slots) {
        if (slots.size == totalSlots) {
            val purchased = slots.first()
            val rewards = slots.drop(1)
            onComplete(purchased, rewards)
        }
    }

    val filledCount = slots.size
    val remaining = totalSlots - filledCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = promotion.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$filledCount de $totalSlots seleccionados",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remaining > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                )
            }
        },
        text = {
            Column {
                // Slot badges -- show what has been selected so far.
                if (slots.isNotEmpty()) {
                    BogoSlotSummaryRow(slots = slots, bogo = bogo)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                if (eligibleProducts.isEmpty()) {
                    Text(
                        text = "No hay productos elegibles disponibles.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = if (remaining > 0) "Selecciona $remaining mas:" else "Seleccion completa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(eligibleProducts, key = { it.id }) { item ->
                            val count = countByProductId[item.id] ?: 0
                            BogoEligibleItemRow(
                                item = item,
                                count = count,
                                canAdd = filledCount < totalSlots,
                                onAdd = {
                                    if (slots.size < totalSlots) {
                                        slots = slots + item
                                    }
                                },
                                onRemove = {
                                    val lastIdx = slots.indexOfLast { it.id == item.id }
                                    if (lastIdx >= 0) slots = slots.toMutableList().also { it.removeAt(lastIdx) }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun BogoSlotSummaryRow(slots: List<MenuItem>, bogo: PromotionBenefit.BuyXGetY) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.forEachIndexed { index, item ->
            val isPurchased = index == 0
            val badgeLabel = if (isPurchased) {
                if (bogo.buyQuantity > 1) "Pago x${bogo.buyQuantity}" else "Pago"
            } else {
                "Gratis"
            }
            val badgeColor = if (isPurchased)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
            val badgeContentColor = if (isPurchased)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onTertiaryContainer

            Card(
                colors = CardDefaults.cardColors(containerColor = badgeColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.nombre,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = badgeContentColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = if (isPurchased)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPurchased)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BogoEligibleItemRow(
    item: MenuItem,
    count: Int,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (count > 0)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (count > 0)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
        else
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.precio.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", item.precio)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onRemove,
                    enabled = count > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Quitar", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 32.dp).padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center
                )
                FilledTonalIconButton(
                    onClick = onAdd,
                    enabled = canAdd,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

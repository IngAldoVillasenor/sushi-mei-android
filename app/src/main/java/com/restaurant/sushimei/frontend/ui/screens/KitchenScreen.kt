package com.restaurant.sushimei.frontend.ui.screens

import com.restaurant.sushimei.frontend.ui.util.formatCurrency

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.restaurant.sushimei.frontend.KitchenViewModel
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.model.OrderRecord
import com.restaurant.sushimei.frontend.data.model.OrderStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.FulfillmentType
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class OrderUI(
    val id: String,
    val deliveryType: String,
    val details: String,
    val isPending: Boolean
)

@Composable

fun KitchenScreen(viewModel: KitchenViewModel = run {
    val context = LocalContext.current

    viewModel(factory = KitchenViewModel.factory(context))
}) {
    val backendSummaries by viewModel.operationalSummaries.collectAsState()

    val detailCache by viewModel.orderDetailCache.collectAsState()

    val localOrders by viewModel.localOrders.collectAsState()

    val pendingBackend = backendSummaries.filter {
        it.orderSource != "ANDROID_MANUAL" &&
            (it.status == "PENDING_VALIDATION" || it.status == "PENDING")
    }

    val preparingBackend = backendSummaries.filter { it.status == "PREPARING" }

    val readyBackend = backendSummaries.filter { it.status == "READY" }

    val pendingLocal = localOrders.filter { it.status == OrderStatus.PENDING }

    val preparingLocal = localOrders.filter { it.status == OrderStatus.PREPARING }

    val readyLocal = localOrders.filter { it.status == OrderStatus.READY }

    val errorMsg by viewModel.kitchenError.collectAsState()

    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(errorMsg!!) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }

    Row(
        modifier = Modifier

            .fillMaxSize()

            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Columna 1: Nuevos Pedidos (PENDING) ──────────────────────────────

        Column(modifier = Modifier.weight(1f)) {
            KitchenColumnHeader("🔴", "Nuevos Pedidos", pendingLocal.size + pendingBackend.size)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pendingLocal) { order ->

                    LocalOrderCard(order, "🖨️ Aceptar e Imprimir", MaterialTheme.colorScheme.primary, Color(0xFFFFF9C4)) { viewModel.acceptLocalOrder(order, it) }
                }

                items(pendingBackend) { summary ->

                    OperationalOrderCard(summary, detailCache[summary.id], viewModel)
                }
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = Color.LightGray, thickness = 2.dp)

        // ── Columna 2: Cocinando (PREPARING) ─────────────────────────────────

        Column(modifier = Modifier.weight(1f)) {
            KitchenColumnHeader("🔥", "Cocinando", preparingLocal.size + preparingBackend.size)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(preparingLocal) { order ->

                    LocalOrderCard(order, "✅ Marcar como Listo", Color(0xFF388E3C), Color(0xFFE8F5E9)) { viewModel.markLocalOrderReady(order.id) }
                }

                items(preparingBackend) { summary ->

                    OperationalOrderCard(summary, detailCache[summary.id], viewModel)
                }
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = Color.LightGray, thickness = 2.dp)

        // ── Columna 3: Listos para Entrega (READY) ───────────────────────────

        Column(modifier = Modifier.weight(1f)) {
            KitchenColumnHeader("🟢", "Listos para Entrega", readyLocal.size + readyBackend.size)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(readyLocal) { order ->

                    LocalOrderCard(order, "🏍️ Despachar", Color(0xFF1565C0), Color(0xFFE3F2FD)) { viewModel.dispatchLocalOrder(order.id) }
                }

                items(readyBackend) { summary ->

                    OperationalOrderCard(summary, detailCache[summary.id], viewModel)
                }
            }

            if (readyLocal.isEmpty() && readyBackend.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 40.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Sin pedidos listos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

// Composables auxiliares

// ─────────────────────────────────────────────────────────────────────────────

@Composable

private fun KitchenColumnHeader(emoji: String, title: String, count: Int) {
    Text(
        text = "$emoji $title ($count)",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/**

 * Tarjeta para órdenes locales generadas desde el POS.

 * El botón de acción y colores se configuran según el status.

 */

@Composable

fun LocalOrderCard(
    order: Order,
    primaryAction: String,
    primaryColor: Color,
    containerColor: Color,
    onPrimary: (context: android.content.Context) -> Unit
) {
    val context = LocalContext.current

    val currentLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)

    val timeFormat = remember(currentLocale) { SimpleDateFormat("HH:mm", currentLocale) }

    val horaCreacion = timeFormat.format(Date(order.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Ticket # y hora

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ticket #${order.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = horaCreacion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "MOSTRADOR",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE65100),
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 2.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Lista de productos

            order.items.forEach { configuredProduct ->

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${configuredProduct.quantity}x ${configuredProduct.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${formatCurrency(configuredProduct.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (configuredProduct.omittedComponents.isNotEmpty()) {
                    val omissions = configuredProduct.omittedComponents.joinToString(", ") { comp ->
                        if (!comp.detail.isNullOrBlank()) {
                            "${comp.displayName} (${comp.detail})"
                        } else {
                            comp.displayName
                        }
                    }
                    Text(
                        text = "SIN: $omissions",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFD32F2F)
                    )
                }

                configuredProduct.groups.forEach { group ->
                    group.selections.forEach { sel ->
                        Text("   + ${sel.name}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    }
                }

                if (!configuredProduct.note.isNullOrBlank()) {
                    Text(
                        text = "NOTA: ${configuredProduct.note}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFE65100)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Total

            Row(
                modifier = Modifier

                    .fillMaxWidth()

                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total:", fontWeight = FontWeight.Bold)

                Text(
                    text = "${formatCurrency(order.total)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón de acción principal

            Button(
                onClick = { onPrimary(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(primaryAction, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

// Composables existentes para órdenes del backend (Retrofit) — sin cambios

// ─────────────────────────────────────────────────────────────────────────────

@Composable

fun ReceiptImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.DarkGray,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Comprobante de Transferencia",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Foto del comprobante",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier

                        .fillMaxWidth()

                        .height(400.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable

fun OperationalOrderCard(summary: OperationalOrderSummaryDto, detail: OperationalOrderDetailDto?, viewModel: KitchenViewModel) {
    val isPending = summary.status == "PENDING" || summary.status == "PENDING_VALIDATION"

    val context = LocalContext.current

    var showReceiptDialog by remember { mutableStateOf(false) }

    if (showReceiptDialog && detail?.transferReceiptPath != null) {
        ReceiptImageDialog(
            imageUrl = "${com.restaurant.sushimei.frontend.BuildConfig.BASE_URL}uploads/${detail.transferReceiptPath}",
            onDismiss = { showReceiptDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isPending) Color(0xFFFFF9C4) else Color(0xFFE8F5E9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Ticket: #${summary.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text(
                    text = when (summary.fulfillmentType) {
                        FulfillmentType.DELIVERY -> "DOMICILIO"

                        FulfillmentType.PICKUP -> "MOSTRADOR"

                        else -> "LEGACY/DESCONOCIDO"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (summary.fulfillmentType) {
                        FulfillmentType.DELIVERY -> Color.Blue

                        FulfillmentType.PICKUP -> Color(0xFFE65100)

                        else -> Color.Gray
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (summary.fulfillmentType == FulfillmentType.DELIVERY) {
                Text("Dirección: ${summary.deliveryAddress ?: "No especificada"}", fontWeight = FontWeight.Bold)

                if (summary.paymentMethod == PaymentMethod.CASH && summary.cashDenomination != null) {
                    Text("Paga con: ${formatCurrency(summary.cashDenomination)}", color = Color.DarkGray)

                    if (summary.total != null) {
                        val change = summary.cashDenomination - summary.total
                        Text("Cambio: ${formatCurrency(change)}", color = Color.Red, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Cambio: NO DISPONIBLE", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (summary.fulfillmentType == FulfillmentType.PICKUP) {
                Text("Nombre: ${summary.pickupName ?: "No especificado"}", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (detail != null) {
                if (detail.lines.isNotEmpty()) {
                    detail.lines.forEach { line ->

                        Text("${line.quantity}x ${line.name} (${formatCurrency(line.finalLineTotal)})")

                        if (line.omittedComponents.isNotEmpty()) {
                            val omissions = line.omittedComponents.joinToString(", ") { comp ->
                                if (!comp.detail.isNullOrBlank()) {
                                    "${comp.displayName} (${comp.detail})"
                                } else {
                                    comp.displayName
                                }
                            }
                            Text(
                                text = "SIN: $omissions",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFD32F2F) // Material Red 700
                            )
                        }

                        line.configuration.forEach { config ->
                            Text("   + ${config.itemName}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        }

                        if (!line.note.isNullOrBlank()) {
                            Text(
                                text = "NOTA: ${line.note}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE65100) // Material Orange 900
                            )
                        }
                    }
                } else if (!detail.legacyOrderDetails.isNullOrBlank()) {
                    Text(text = detail.legacyOrderDetails, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text("Cargando detalles...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (summary.status) {
                    "PENDING_VALIDATION" -> {
                        OutlinedButton(onClick = { showReceiptDialog = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("🔍 Ver Comprobante")
                        }

                        Button(onClick = { viewModel.validatePaymentOperational(summary.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) {
                            Text("✅ Validar Pago")
                        }
                    }

                    "PENDING" -> {
                        Button(onClick = { viewModel.acceptOperationalOrder(summary.id, context) }) {
                            Text("🖨️ Aceptar e Imprimir")
                        }
                    }

                    "PREPARING" -> {
                        Button(onClick = { viewModel.markOperationalOrderReady(summary.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                            Text("✅ Marcar como Listo")
                        }
                    }

                    "READY" -> {
                        Button(onClick = { viewModel.completeOperationalOrder(summary.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))) {
                            Text("🏍️ Entregado / Despachar")
                        }
                    }
                }
            }
        }
    }
}

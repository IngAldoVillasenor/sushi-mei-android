package com.restaurant.sushimei.frontend.ui.screens

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

    // Órdenes del backend (Retrofit — actualmente vacías hasta que el backend exista)
    val backendOrders by viewModel.backendOrders.collectAsState()

    // Órdenes locales del POS
    val localOrders by viewModel.localOrders.collectAsState()

    // Separar por status
    val pendingBackend  = backendOrders.filter { it.status == "PENDING" }
    val preparingBackend = backendOrders.filter { it.status == "PREPARING" }

    val pendingLocal   = localOrders.filter { it.status == OrderStatus.PENDING }
    val preparingLocal = localOrders.filter { it.status == OrderStatus.PREPARING }
    val readyLocal     = localOrders.filter { it.status == OrderStatus.READY }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Columna 1: Nuevos Pedidos (PENDING) ──────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            KitchenColumnHeader(
                emoji = "🔴",
                title = "Nuevos Pedidos",
                count = pendingLocal.size + pendingBackend.size
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pendingLocal) { order ->
                    LocalOrderCard(
                        order = order,
                        primaryAction = "🖨️ Aceptar e Imprimir",
                        primaryColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color(0xFFFFF9C4),
                        onPrimary = { viewModel.acceptLocalOrder(order, it) }
                    )
                }
                items(pendingBackend) { order ->
                    OrderCardReal(order = order, viewModel = viewModel)
                }
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = Color.LightGray, thickness = 2.dp)

        // ── Columna 2: Cocinando (PREPARING) ─────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            KitchenColumnHeader(
                emoji = "🔥",
                title = "Cocinando",
                count = preparingLocal.size + preparingBackend.size
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(preparingLocal) { order ->
                    LocalOrderCard(
                        order = order,
                        primaryAction = "✅ Marcar como Listo",
                        primaryColor = Color(0xFF388E3C),
                        containerColor = Color(0xFFE8F5E9),
                        onPrimary = { viewModel.markLocalOrderReady(order.id) }
                    )
                }
                items(preparingBackend) { order ->
                    OrderCardReal(order = order, viewModel = viewModel)
                }
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = Color.LightGray, thickness = 2.dp)

        // ── Columna 3: Listos para Entrega (READY) ───────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            KitchenColumnHeader(
                emoji = "🟢",
                title = "Listos para Entrega",
                count = readyLocal.size
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(readyLocal) { order ->
                    LocalOrderCard(
                        order = order,
                        primaryAction = "🏍️ Despachar",
                        primaryColor = Color(0xFF1565C0),
                        containerColor = Color(0xFFE3F2FD),
                        onPrimary = { viewModel.dispatchLocalOrder(order.id) }
                    )
                }
            }

            // Estado vacío: nadie esperando
            if (readyLocal.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sin pedidos listos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
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
                        text = "$${String.format(java.util.Locale.US, "%.2f", configuredProduct.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                    text = "$${String.format(java.util.Locale.US, "%.2f", order.total)}",
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
fun OrderCardReal(order: OrderRecord, viewModel: KitchenViewModel) {
    val isPending = order.status == "PENDING" || order.status == "PENDING_VALIDATION"

    val context = LocalContext.current

    var showReceiptDialog by remember { mutableStateOf(false) }

    if (showReceiptDialog && order.transferReceiptPath != null) {
        val imageUrl = "http://10.0.2.2:8080/uploads/${order.transferReceiptPath}"

        ReceiptImageDialog(
            imageUrl = imageUrl,
            onDismiss = { showReceiptDialog = false }
        )
    }

    var showAuditDialog by remember { mutableStateOf(false) }

    if (showAuditDialog) {
        AuditOrderDialog(
            order = order,
            onConfirm = {
                showAuditDialog = false
                viewModel.acceptOrder(order, context)
            },
            onDismiss = {
                showAuditDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) Color(0xFFFFF9C4) else Color(0xFFE8F5E9)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ticket: #${order.id}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = order.deliveryType,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (order.deliveryType == "DOMICILIO") Color.Blue else Color(0xFFE65100),
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = order.orderDetails, style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (order.status) {
                    "PENDING_VALIDATION" -> {
                        OutlinedButton(
                            onClick = { showReceiptDialog = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("🔍 Ver Comprobante")
                        }

                        Button(
                            onClick = { viewModel.validatePayment(order.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                        ) {
                            Text("✅ Validar Pago")
                        }
                    }
                    "PENDING" -> {
                        OutlinedButton(
                            onClick = { /* Lógica de rechazo */ },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Rechazar")
                        }
                        Button(onClick = { showAuditDialog = true }) {
                            Text("🖨️ Aceptar e Imprimir")
                        }
                    }
                    "PREPARING" -> {
                        Button(
                            onClick = { viewModel.completeOrder(order.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("✅ Despachar (Listo)")
                        }
                    }
                }
            }
        }
    }
}

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
fun AuditOrderDialog(
    order: OrderRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Auditar Orden #${order.id}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Tipo: ${order.deliveryType}", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                Text("Dirección / Nombre:", fontWeight = FontWeight.Bold)
                Text(order.deliveryAddress ?: "No especificado", color = Color.Red)

                Spacer(modifier = Modifier.height(8.dp))

                Text("Detalle del Pedido:", fontWeight = FontWeight.Bold)
                Text(order.orderDetails)

                Spacer(modifier = Modifier.height(8.dp))

                Text("Método de Pago:", fontWeight = FontWeight.Bold)
                Text(order.paymentNotes ?: "No especificado", color = Color(0xFF2E7D32))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("🖨️ Imprimir y Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Volver")
            }
        }
    )
}

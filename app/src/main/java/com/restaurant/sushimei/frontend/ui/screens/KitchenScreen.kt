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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.restaurant.sushimei.frontend.KitchenViewModel
import com.restaurant.sushimei.frontend.data.model.OrderRecord

data class OrderUI(
    val id: String,
    val deliveryType: String,
    val details: String,
    val isPending: Boolean
)

@Composable
fun KitchenScreen(viewModel: KitchenViewModel = viewModel()) {
    // Escuchamos la lista real de la base de datos
    val activeOrders by viewModel.orders.collectAsState()

    // Filtramos usando los estados de la base de datos (PENDING y PREPARING)
    val pendingOrders = activeOrders.filter { it.status == "PENDING" }
    val preparingOrders = activeOrders.filter { it.status == "PREPARING" }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Columna Izquierda: Nuevos Pedidos
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "🔴 Nuevos Pedidos (${pendingOrders.size})",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pendingOrders) { order ->
                    OrderCardReal(order = order, viewModel = viewModel)
                }
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = Color.LightGray,
            thickness = 2.dp
        )

        // Columna Derecha: Cocinando
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "🔥 Cocinando (${preparingOrders.size})",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(preparingOrders) { order ->
                    OrderCardReal(order = order, viewModel = viewModel)
                }
            }
        }
    }
}

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

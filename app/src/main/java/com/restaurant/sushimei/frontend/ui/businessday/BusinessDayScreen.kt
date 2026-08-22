package com.restaurant.sushimei.frontend.ui.businessday
import com.restaurant.sushimei.frontend.ui.util.formatCurrency
import com.restaurant.sushimei.frontend.ui.util.rememberBluetoothPermissionGateway
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.restaurant.sushimei.frontend.data.local.provideBusinessDayRepository
import com.restaurant.sushimei.frontend.data.local.providePrintManager


private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.of("America/Mexico_City"))
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun formatInstant(instant: java.time.Instant?): String {
    if (instant == null) return "N/A"
    return timeFormatter.format(instant)
}

private fun formatDate(isoDate: String): String {
    return try {
        LocalDate.parse(isoDate).format(dateFormatter)
    } catch (e: Exception) {
        isoDate
    }
}
@Composable
fun BusinessDayScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: BusinessDayViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BusinessDayViewModel(
                    repository = provideBusinessDayRepository(context),
                    printManager = providePrintManager(context),
                    printJobRepository = com.restaurant.sushimei.frontend.data.local.providePrintJobRepository(context)
                ) as T
            }
        }
    )

    val state by viewModel.state.collectAsState()
    val printMessage by viewModel.printMessage.collectAsState()
    val reopenMessage by viewModel.reopenMessage.collectAsState()
    var showReopenDialog by remember { mutableStateOf(false) }
    val isPrinting by viewModel.isPrinting.collectAsState()
    val requireBluetoothPermission = rememberBluetoothPermissionGateway()
    var inputAmount by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(printMessage) {
        printMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearPrintMessage()
        }
    }

    LaunchedEffect(reopenMessage) {
        reopenMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearReopenMessage()
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("Volver")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Gestión de Día", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val currState = state) {
            is BusinessDayState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is BusinessDayState.Error -> {
                Text("Error: ${currState.message}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.loadCurrentBusinessDay() }) {
                    Text("Reintentar")
                }
            }
            is BusinessDayState.NotOpen -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No hay un día abierto.", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputAmount,
                            onValueChange = { inputAmount = it },
                            label = { Text("Monto Inicial en Efectivo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val amount = inputAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                viewModel.openBusinessDay(amount)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = inputAmount.isNotBlank() && (inputAmount.toBigDecimalOrNull() != null)
                        ) {
                            Text("Abrir Día")
                        }
                    }
                }
            }
                        is BusinessDayState.Open -> {
                val day = currState.day
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DÍA ABIERTO", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Caja inicial", style = MaterialTheme.typography.labelMedium)
                        Text("${formatCurrency(day.openingCashAmount)}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Apertura", style = MaterialTheme.typography.labelMedium)
                        Text(formatInstant(day.openedAt), style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Los totales de ventas y la conciliación se calcularán al cerrar el día.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = inputAmount,
                            onValueChange = { inputAmount = it },
                            label = { Text("Caja contada") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val amount = inputAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                viewModel.closeBusinessDay(amount)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = inputAmount.isNotBlank() && (inputAmount.toBigDecimalOrNull() != null)
                        ) {
                            Text("Cerrar día")
                        }
                    }
                }
            }
            is BusinessDayState.Closed -> {
                val day = currState.day
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("CIERRE DEL DÍA", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Fecha: ${formatDate(day.businessDate)}")
                        Text("Apertura: ${formatInstant(day.openedAt)}")
                        Text("Cierre: ${formatInstant(day.closedAt)}")

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Text("Fondo Inicial: ${formatCurrency(day.openingCashAmount)}")
                        Text("Ventas Efectivo: ${formatCurrency(day.cashSalesAmount)}")
                        Text("Efectivo Esperado: ${formatCurrency(day.expectedClosingCashAmount)}")
                        Text("Efectivo Contado: ${formatCurrency(day.actualClosingCashAmount)}")
                        Text("Diferencia: ${formatCurrency(day.cashDifferenceAmount)}")

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { requireBluetoothPermission { viewModel.printClosingTicket(day) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPrinting
                        ) {
                            Text(if (isPrinting) "Imprimiendo..." else "Imprimir cierre")
                        }

                        Button(
                            onClick = { showReopenDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = !isPrinting
                        ) {
                            Text("Reabrir día")
                        }
                    }
                }

                if (showReopenDialog) {
                    AlertDialog(
                        onDismissRequest = { showReopenDialog = false },
                        title = { Text("Reabrir día") },
                        text = { Text("El día ya fue cerrado. Si lo reabres, podrán registrarse nuevas ventas y será necesario realizar un nuevo cierre.\n\nEl cierre anterior se conservará en el historial.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showReopenDialog = false
                                    viewModel.reopenBusinessDay()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Reabrir día")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showReopenDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

            }
        }
    }
    }
}

package com.restaurant.sushimei.frontend.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import com.restaurant.sushimei.frontend.ui.util.formatCurrency
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionConfirmationDialog(
    order: OperationalOrderSummaryDto,
    isCollectionInFlight: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (PaymentMethod, BigDecimal?) -> Unit,
    titleOverride: String? = null,
    submitLabelOverride: String? = null
) {
    var selectedMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var cashDenomination by remember { mutableStateOf("") }
    var isDenomValid by remember { mutableStateOf(false) }
    var showDenomError by remember { mutableStateOf(false) }

    fun validateDenom(input: String): Boolean {
        if (selectedMethod != PaymentMethod.CASH) return true
        val denom = input.toBigDecimalOrNull()
        return denom != null && (order.total == null || denom >= order.total)
    }

    val title = titleOverride ?: "Cobrar Pedido #${order.id}"
    val submitLabel = submitLabelOverride ?: "Cobrar"

    Dialog(onDismissRequest = { if (!isCollectionInFlight) onDismiss() }) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "Total a cobrar: ${order.total?.let { formatCurrency(it) } ?: "N/A"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text("Método de pago", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val methods = listOf(PaymentMethod.CASH, PaymentMethod.TRANSFER, PaymentMethod.CARD)
                    val labels = listOf("Efectivo", "Transferencia", "Tarjeta")

                    methods.zip(labels).forEach { (method, label) ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = {
                                selectedMethod = method
                                if (method != PaymentMethod.CASH) {
                                    cashDenomination = ""
                                    showDenomError = false
                                } else {
                                    isDenomValid = validateDenom(cashDenomination)
                                }
                            },
                            label = { Text(label) },
                            enabled = !isCollectionInFlight
                        )
                    }
                }

                if (selectedMethod == PaymentMethod.CASH) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = cashDenomination,
                        onValueChange = {
                            cashDenomination = it
                            isDenomValid = validateDenom(it)
                            showDenomError = it.isNotEmpty() && !isDenomValid
                        },
                        label = { Text("¿Con cuánto paga?") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = showDenomError,
                        enabled = !isCollectionInFlight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showDenomError) {
                        Text(
                            "Debe ser mayor o igual a ${order.total?.let { formatCurrency(it) } ?: "0"}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (isDenomValid && cashDenomination.toBigDecimalOrNull() != null && order.total != null) {
                        val change = cashDenomination.toBigDecimal() - order.total
                        Text(
                            "Cambio: ${formatCurrency(change)}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isCollectionInFlight) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Procesando...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !isCollectionInFlight) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedMethod != null) {
                                    val denom = if (selectedMethod == PaymentMethod.CASH) cashDenomination.toBigDecimalOrNull() else null
                                    onSubmit(selectedMethod!!, denom)
                                }
                            },
                            enabled = selectedMethod != null && (selectedMethod != PaymentMethod.CASH || isDenomValid) && !isCollectionInFlight
                        ) {
                            Text(submitLabel)
                        }
                    }
                }
            }
        }
    }
}

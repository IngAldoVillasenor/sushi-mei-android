package com.restaurant.sushimei.frontend
import com.restaurant.sushimei.frontend.ui.util.formatCurrency
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate



import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.model.OrderRecord
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID


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

class PrintService(private val context: Context) {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun writeSafelyToPrinter(
        outputStream: OutputStream,
        data: ByteArray
    ) {
        val chunkSize = 256
        var offset = 0

        while (offset < data.size) {
            val length = minOf(chunkSize, data.size - offset)

            outputStream.write(data, offset, length)
            outputStream.flush()

            offset += length

            if (offset < data.size) {
                Thread.sleep(25L)
            }
        }

        Thread.sleep(500L)
    }

    private fun openCashDrawer(outputStream: OutputStream) {
        val drawerPulse = byteArrayOf(
            0x1B,             // ESC
            0x70,             // p
            0x00,             // m = pin 2
            0x19,             // 25 * 2 ms = 50 ms ON
            0xFA.toByte()     // 250 * 2 ms = 500 ms OFF
        )

        outputStream.write(drawerPulse)
        outputStream.flush()

        // Le damos tiempo a la impresora para ejecutar físicamente el pulso.
        Thread.sleep(200L)
    }

    @SuppressLint("MissingPermission")

    fun printTicket(order: OrderRecord): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                println("⚠️ Permiso BLUETOOTH_CONNECT no concedido.")

                return false
            }
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            println("❌ Bluetooth apagado o no soportado.")

            return false
        }

        val pairedDevices = bluetoothAdapter.bondedDevices

        if (pairedDevices.isEmpty()) {
            println("⚠️ No hay impresoras emparejadas en la tablet.")

            return false
        }

        val printerDevice = pairedDevices.first()

        return try {
            val socket = printerDevice.createRfcommSocketToServiceRecord(SPP_UUID)

            socket.connect()

            val outputStream = socket.outputStream

            imprimirFormatoEscPos(outputStream, order)

            outputStream.flush()

            socket.close()

            true
        } catch (e: Exception) {
            println("⚠️ Error Bluetooth: ${e.message}")

            false
        }
    }

    /**

     * Versión de impresión para órdenes locales del POS ([Order]).

     * Genera el mismo formato ESC/POS que [printTicket] pero a partir del modelo de dominio.

     */

    @SuppressLint("MissingPermission")

    fun printLocalOrderTicket(order: Order): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return false

        val pairedDevices = bluetoothAdapter.bondedDevices

        if (pairedDevices.isEmpty()) return false

        return try {
            val socket = pairedDevices.first().createRfcommSocketToServiceRecord(SPP_UUID)

            socket.connect()

            imprimirOrdenLocal(socket.outputStream, order)

            socket.outputStream.flush()

            socket.close()

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun imprimirFormatoEscPos(out: OutputStream, order: OrderRecord) {
        val ESC: Byte = 0x1B

        val GS: Byte = 0x1D

        val init = byteArrayOf(ESC, 0x40)

        val center = byteArrayOf(ESC, 0x61, 1)

        val left = byteArrayOf(ESC, 0x61, 0)

        val boldOn = byteArrayOf(ESC, 0x45, 1)

        val boldOff = byteArrayOf(ESC, 0x45, 0)

        val cut = byteArrayOf(GS, 0x56, 0x41, 0x10) // Cortar papel (si soporta)

        out.write(init)

        // Cabecera

        out.write(center)

        out.write(boldOn)

        out.write("SUSHI MEI\n".toByteArray())

        out.write(boldOff)

        out.write("================================\n".toByteArray())

        // Datos del pedido

        out.write(left)

        out.write("Ticket: #${order.id}\n".toByteArray())

        out.write("Tipo: ${order.deliveryType}\n".toByteArray())

        if (order.deliveryType == "DOMICILIO" && !order.deliveryAddress.isNullOrBlank()) {
            out.write("Direccion: ${order.deliveryAddress}\n".toByteArray())
        }

        out.write("--------------------------------\n".toByteArray())

        out.write(boldOn)

        out.write("DETALLE DEL PEDIDO:\n".toByteArray())

        out.write(boldOff)

        out.write("${order.orderDetails}\n".toByteArray())

        out.write("--------------------------------\n".toByteArray())

        if (order.paymentNotes != null) {
            out.write("Notas: ${order.paymentNotes}\n".toByteArray())

            out.write("--------------------------------\n".toByteArray())
        }

        // Pie de página

        out.write(center)

        out.write("A cocinar!\n".toByteArray())

        // Espacio para que el ticket salga lo suficiente

        out.write("\n\n\n\n".toByteArray())

        out.write(cut)
    }

    private fun imprimirOrdenLocal(out: OutputStream, order: com.restaurant.sushimei.frontend.data.model.Order) {
        val ESC: Byte = 0x1B

        val GS: Byte = 0x1D

        val init    = byteArrayOf(ESC, 0x40)

        val center  = byteArrayOf(ESC, 0x61, 1)

        val left    = byteArrayOf(ESC, 0x61, 0)

        val boldOn  = byteArrayOf(ESC, 0x45, 1)

        val boldOff = byteArrayOf(ESC, 0x45, 0)

        val cut     = byteArrayOf(GS, 0x56, 0x41, 0x10)

        out.write(init)

        out.write(center)

        out.write(boldOn)

        out.write("SUSHI MEI\n".toByteArray())

        out.write(boldOff)

        out.write("================================\n".toByteArray())

        out.write(left)

        out.write("Ticket: #${order.id}\n".toByteArray())

        out.write("Tipo: MOSTRADOR\n".toByteArray())

        out.write("--------------------------------\n".toByteArray())

        out.write(boldOn)

        out.write("PRODUCTOS:\n".toByteArray())

        out.write(boldOff)

        order.items.forEach { configuredProduct ->

            val linea = "${configuredProduct.quantity}x ${configuredProduct.name}" +

                    "  ${formatCurrency(configuredProduct.total)}\n"

            out.write(linea.toByteArray())

            if (configuredProduct.omittedComponents.isNotEmpty()) {
                val omissions = configuredProduct.omittedComponents.joinToString(", ") { comp ->
                    if (!comp.detail.isNullOrBlank()) {
                        "${comp.displayName} (${comp.detail})"
                    } else {
                        comp.displayName
                    }
                }
                out.write(boldOn)
                out.write("   SIN: $omissions\n".toByteArray())
                out.write(boldOff)
            }

            configuredProduct.groups.forEach { group ->
                group.selections.forEach { sel ->
                    out.write("   + ${sel.name}\n".toByteArray())
                }
            }

            if (!configuredProduct.note.isNullOrBlank()) {
                out.write(boldOn)
                out.write("   NOTA: ${configuredProduct.note}\n".toByteArray())
                out.write(boldOff)
            }
        }

        out.write("================================\n".toByteArray())

        out.write(boldOn)

        out.write("TOTAL: ${formatCurrency(order.total)}\n".toByteArray())

        out.write(boldOff)

        out.write(center)

        out.write("A cocinar!\n".toByteArray())

        out.write("\n\n\n\n".toByteArray())

        out.write(cut)
    }

    @SuppressLint("MissingPermission")

    fun printClosingTicket(day: com.restaurant.sushimei.frontend.data.model.BusinessDayResponse): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        val bluetoothManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return false
        val pairedDevices = bluetoothAdapter.bondedDevices
        if (pairedDevices.isEmpty()) return false
        val printerDevice = pairedDevices.first()

        return try {
            val socket = printerDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val outputStream = socket.outputStream

            val out = java.io.ByteArrayOutputStream()
            val ESC: Byte = 0x1B
            val GS: Byte = 0x1D
            val init = byteArrayOf(ESC, 0x40)
            val center = byteArrayOf(ESC, 0x61, 1)
            val left = byteArrayOf(ESC, 0x61, 0)
            val boldOn = byteArrayOf(ESC, 0x45, 1)
            val boldOff = byteArrayOf(ESC, 0x45, 0)
            val cut = byteArrayOf(GS, 0x56, 0x41, 0x10)

            out.write(init)
            out.write(center)
            out.write(boldOn)
            out.write("=== CIERRE DEL DIA ===\n".toByteArray())
            out.write(boldOff)
            out.write(left)
            out.write("Dia: ${formatDate(day.businessDate)}\n".toByteArray())
            out.write("Abierto: ${formatInstant(day.openedAt)}\n".toByteArray())
            out.write("Cerrado: ${formatInstant(day.closedAt)}\n".toByteArray())
            out.write("--------------------------------\n".toByteArray())
            out.write("Ventas Completadas: ${day.completedOrderCount}\n".toByteArray())
            out.write("Ventas Anuladas: ${day.voidedOrderCount}\n".toByteArray())
            out.write("Monto Total: ${formatCurrency(day.completedSalesAmount)}\n".toByteArray())
            out.write("--------------------------------\n".toByteArray())
            out.write("Efectivo: ${formatCurrency(day.cashSalesAmount)}\n".toByteArray())
            out.write("Transferencia: ${formatCurrency(day.transferSalesAmount)}\n".toByteArray())
            out.write("Tarjeta: ${formatCurrency(day.cardSalesAmount)}\n".toByteArray())
            out.write("No Clasificado: ${formatCurrency(day.unclassifiedSalesAmount)}\n".toByteArray())
            out.write("--------------------------------\n".toByteArray())
            out.write("Fondo Inicial: ${formatCurrency(day.openingCashAmount)}\n".toByteArray())
            out.write("Efectivo Esperado: ${formatCurrency(day.expectedClosingCashAmount)}\n".toByteArray())
            out.write("Efectivo Contado: ${formatCurrency(day.actualClosingCashAmount)}\n".toByteArray())
            out.write("Diferencia: ${formatCurrency(day.cashDifferenceAmount)}\n".toByteArray())
            out.write("================================\n".toByteArray())
            out.write("\n\n\n\n".toByteArray())
            out.write(cut)

            writeSafelyToPrinter(outputStream, out.toByteArray())
            socket.close()

            true
        } catch (e: Exception) {
            false
        }
    }

    fun printOperationalTicket(order: OperationalOrderDetailDto, isReprint: Boolean = false, isInternalCopy: Boolean = false): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return false

        val pairedDevices = bluetoothAdapter.bondedDevices

        if (pairedDevices.isEmpty()) return false

        val printerDevice = pairedDevices.first()

        return try {
            val socket = printerDevice.createRfcommSocketToServiceRecord(SPP_UUID)

            socket.connect()

            val outputStream = socket.outputStream

            val formattedText = formatOperationalTicket(order, isReprint, isInternalCopy)

            writeSafelyToPrinter(outputStream, formattedText)

            if (!isReprint && !isInternalCopy) {
                openCashDrawer(outputStream)
            }

            socket.close()

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun printConfigurationTree(
        out: OutputStream,
        configList: List<com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto>,
        parentId: Long?,
        indentLevel: Int
    ) {
        val children = configList.filter { it.parentSelectionSnapshotId == parentId }
        for (child in children) {
            if (child.displayOnTicket) {
                val indent = "   ".repeat(indentLevel)
                out.write("${indent}+ ${child.itemName}\n".toByteArray())
                for (omission in child.omittedComponents) {
                    out.write("${indent}   SIN: ${omission.displayName}\n".toByteArray())
                }
                if (!child.note.isNullOrBlank()) {
                    out.write("${indent}   NOTA: ${child.note}\n".toByteArray())
                }
                printConfigurationTree(out, configList, child.id, indentLevel + 1)
            } else {
                printConfigurationTree(out, configList, child.id, indentLevel)
            }
        }
    }

    fun formatOperationalTicket(order: com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto, isReprint: Boolean = false, isInternalCopy: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()

        val ESC: Byte = 0x1B

        val GS: Byte = 0x1D

        val init = byteArrayOf(ESC, 0x40)

        val center = byteArrayOf(ESC, 0x61, 1)

        val left = byteArrayOf(ESC, 0x61, 0)

        val boldOn = byteArrayOf(ESC, 0x45, 1)

        val boldOff = byteArrayOf(ESC, 0x45, 0)

        val cut = byteArrayOf(GS, 0x56, 0x41, 0x10)

        out.write(init)

        // Cabecera

        out.write(center)

        out.write(boldOn)

        out.write("SUSHI MEI\n".toByteArray())

        out.write(boldOff)

        out.write("================================\n".toByteArray())

        if (isInternalCopy) {
            out.write(boldOn)
            out.write("*** COPIA INTERNA ***\n".toByteArray())
            out.write(boldOff)
            out.write("================================\n".toByteArray())
        } else if (isReprint) {
            out.write(boldOn)
            out.write("*** REIMPRESION ***\n".toByteArray())
            out.write(boldOff)
            out.write("================================\n".toByteArray())
        }

        // Datos del pedido

        out.write(left)

        out.write("Ticket: #${order.id}\n".toByteArray())

        if (order.fulfillmentType == com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY) {
            out.write("Tipo: DOMICILIO\n".toByteArray())

            if (!order.deliveryAddress.isNullOrBlank()) {
                out.write("Direccion: ${order.deliveryAddress}\n".toByteArray())
            }

            if (order.phoneNumber != null) {
                out.write("Telefono: ${order.phoneNumber}\n".toByteArray())
            }
        } else if (order.fulfillmentType == com.restaurant.sushimei.frontend.data.model.FulfillmentType.PICKUP) {
            out.write("Tipo: MOSTRADOR\n".toByteArray())

            if (!order.pickupName.isNullOrBlank()) {
                out.write("Nombre: ${order.pickupName}\n".toByteArray())
            }
        } else {
            out.write("Tipo: LEGACY/DESCONOCIDO\n".toByteArray())
        }

        out.write("--------------------------------\n".toByteArray())

        out.write(boldOn)

        out.write("DETALLE DEL PEDIDO:\n".toByteArray())

        out.write(boldOff)

        if (order.lines.isNotEmpty()) {
            order.lines.forEach { line ->
                val lineText = "${line.quantity}x ${line.name}  ${formatCurrency(line.finalLineTotal)}\n"
                out.write(lineText.toByteArray())

                if (line.omittedComponents.isNotEmpty()) {
                    val omissions = line.omittedComponents.joinToString(", ") { comp ->
                        if (!comp.detail.isNullOrBlank()) {
                            "${comp.displayName} (${comp.detail})"
                        } else {
                            comp.displayName
                        }
                    }
                    out.write(boldOn)
                    out.write("   SIN: $omissions\n".toByteArray())
                    out.write(boldOff)
                }

                printConfigurationTree(out, line.configuration, null, 1)

                if (!line.note.isNullOrBlank()) {
                    out.write(boldOn)
                    out.write("   NOTA: ${line.note}\n".toByteArray())
                    out.write(boldOff)
                }
            }
        } else if (!order.legacyOrderDetails.isNullOrBlank()) {
            out.write("${order.legacyOrderDetails}\n".toByteArray())
        }

        out.write("================================\n".toByteArray())

        out.write(boldOn)

        if (order.total != null) {
            out.write("TOTAL: ${formatCurrency(order.total)}\n".toByteArray())
        } else {
            out.write("TOTAL: NO DISPONIBLE\n".toByteArray())
        }

        out.write(boldOff)

        if (order.paymentMethod != null) {
            out.write("Pago: ${order.paymentMethod.name}\n".toByteArray())
        } else {
            out.write("Pago: DESCONOCIDO\n".toByteArray())
        }

        if (order.fulfillmentType == com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY && order.paymentMethod == com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH && order.cashDenomination != null) {
            out.write("Paga con: ${formatCurrency(order.cashDenomination)}\n".toByteArray())

            out.write(boldOn)

            if (order.total != null) {
                val change = order.cashDenomination - order.total
                out.write("Cambio: ${formatCurrency(change)}\n".toByteArray())
            } else {
                out.write("Cambio: NO DISPONIBLE\n".toByteArray())
            }

            out.write(boldOff)
        }

        if (!order.paymentNotes.isNullOrBlank()) {
            out.write("--------------------------------\n".toByteArray())

            out.write("Notas: ${order.paymentNotes}\n".toByteArray())
        }

        // Pie de página

        out.write("--------------------------------\n".toByteArray())

        out.write(center)

        out.write("A cocinar!\n".toByteArray())

        // Espacio

        out.write("\n\n\n\n".toByteArray())

        out.write(cut)

        return out.toByteArray()
    }
}

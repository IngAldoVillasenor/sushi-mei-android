package com.restaurant.sushimei.frontend

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.model.OrderRecord
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

class PrintService(private val context: Context) {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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

                    "  \$${String.format("%.2f", configuredProduct.total)}\n"

            out.write(linea.toByteArray())
        }

        out.write("================================\n".toByteArray())

        out.write(boldOn)

        out.write("TOTAL: \$${String.format("%.2f", order.total)}\n".toByteArray())

        out.write(boldOff)

        out.write(center)

        out.write("A cocinar!\n".toByteArray())

        out.write("\n\n\n\n".toByteArray())

        out.write(cut)
    }

    @SuppressLint("MissingPermission")

    fun printOperationalTicket(order: OperationalOrderDetailDto): Boolean {
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

            val formattedText = formatOperationalTicket(order)

            outputStream.write(formattedText)

            outputStream.flush()

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
                printConfigurationTree(out, configList, child.id, indentLevel + 1)
            } else {
                printConfigurationTree(out, configList, child.id, indentLevel)
            }
        }
    }

    fun formatOperationalTicket(order: com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto): ByteArray {
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

                val lineText = "${line.quantity}x ${line.name}  \$${String.format(java.util.Locale.US, "%.2f", line.finalLineTotal)}\n"

                out.write(lineText.toByteArray())

                printConfigurationTree(out, line.configuration, null, 1)
            }
        } else if (!order.legacyOrderDetails.isNullOrBlank()) {
            out.write("${order.legacyOrderDetails}\n".toByteArray())
        }

        out.write("================================\n".toByteArray())

        out.write(boldOn)

        if (order.total != null) {
            out.write("TOTAL: \$${String.format(java.util.Locale.US, "%.2f", order.total)}\n".toByteArray())
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
            out.write("Paga con: \$${String.format(java.util.Locale.US, "%.2f", order.cashDenomination)}\n".toByteArray())

            out.write(boldOn)

            if (order.total != null) {
                val change = order.cashDenomination - order.total
                out.write("Cambio: \$${String.format(java.util.Locale.US, "%.2f", change)}\n".toByteArray())
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

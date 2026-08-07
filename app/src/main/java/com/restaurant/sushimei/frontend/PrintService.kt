package com.restaurant.sushimei.frontend

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.restaurant.sushimei.frontend.data.model.OrderRecord
import java.io.OutputStream
import java.util.UUID

class PrintService(private val context: Context) {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun printTicket(order: OrderRecord): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            println("⚠️ Bluetooth apagado o no soportado.")
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
}
package com.restaurant.sushimei.frontend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.api.RetrofitClient
import com.restaurant.sushimei.frontend.data.model.OrderRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KitchenViewModel : ViewModel() {

    private val _orders = MutableStateFlow<List<OrderRecord>>(emptyList())
    val orders: StateFlow<List<OrderRecord>> = _orders

    init {
        startPollingOrders()
    }

    private fun startPollingOrders() {
        viewModelScope.launch {
            while (true) {
                fetchOrders()
                delay(5000)
            }
        }
    }

    // Extraemos la consulta para reutilizarla
    private suspend fun fetchOrders() {
        try {
            val response = RetrofitClient.instance.getActiveOrders()
            if (response.isSuccessful) {
                response.body()?.let { orderList ->
                    _orders.value = orderList
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error conectando: ${e.message}")
        }
    }

    fun acceptOrder(order: OrderRecord, context: Context) {
        viewModelScope.launch {
            try {
                // 1. Ejecutamos el Bluetooth en un hilo secundario (IO) para no congelar la app
                val printSuccess = withContext(Dispatchers.IO) {
                    val printService = PrintService(context)
                    printService.printTicket(order)
                }

                if (printSuccess) {
                    println("🖨️ Impresión exitosa, enviando a cocina en base de datos...")
                } else {
                    println("⚠️ Falló la impresión, pero la orden avanzará a cocina de todos modos.")
                }

                // 2. Mandamos el aviso a Spring Boot de que cambia a PREPARING
                val response = RetrofitClient.instance.acceptAndPrepareOrder(order.id)
                if (response.isSuccessful) {
                    // 3. Forzamos la actualización visual para que pase a la columna derecha
                    fetchOrders()
                }
            } catch (e: Exception) {
                println("⚠️ Error procesando la orden: ${e.message}")
            }
        }
    }

    // Method que se ejecutará al presionar "Despachar"
    fun completeOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                // 1. Mandamos el aviso al servidor de que cambia a COMPLETED
                val response = RetrofitClient.instance.completeOrder(orderId)

                if (response.isSuccessful) {
                    // 2. Si es exitoso, actualizamos la pantalla.
                    // Como la base de datos ya no lo reportará ni como PENDING ni PREPARING, desaparecerá.
                    fetchOrders()
                }
            } catch (e: Exception) {
                println("⚠️ Error despachando orden: ${e.message}")
            }
        }
    }

    fun validatePayment(orderId: Long) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.validatePayment(orderId)
                if (response.isSuccessful) {
                    fetchOrders() // Actualizamos la lista para que aparezca el botón de Imprimir
                }
            } catch (e: Exception) {
                println("⚠️ Error validando pago: ${e.message}")
            }
        }
    }
}
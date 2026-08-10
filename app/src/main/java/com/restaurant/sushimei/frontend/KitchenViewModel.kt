package com.restaurant.sushimei.frontend

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.api.RetrofitClient
import com.restaurant.sushimei.frontend.data.local.provideOrderRepository
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.model.OrderRecord
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KitchenViewModel(
    private val orderRepository: IOrderRepository
) : ViewModel() {

    // --- Órdenes del backend (Retrofit) ---
    // Se mantiene intacto. Falla silenciosamente hasta que el backend exista.
    private val _backendOrders = MutableStateFlow<List<OrderRecord>>(emptyList())
    val backendOrders: StateFlow<List<OrderRecord>> = _backendOrders.asStateFlow()

    // --- Órdenes locales del POS (IOrderRepository inyectado) ---
    val localOrders: StateFlow<List<Order>> = orderRepository.activeOrders

    // Alias legacy para KitchenScreen (evita romper el código existente de Retrofit)
    val orders: StateFlow<List<OrderRecord>> = _backendOrders

    init {
        startPollingOrders()
    }

    private fun startPollingOrders() {
        viewModelScope.launch {
            while (true) {
                fetchBackendOrders()
                delay(5000)
            }
        }
    }

    private suspend fun fetchBackendOrders() {
        try {
            val response = RetrofitClient.instance.getActiveOrders()
            if (response.isSuccessful) {
                response.body()?.let { _backendOrders.value = it }
            }
        } catch (e: Exception) {
            // Falla silenciosamente — el backend aún no existe
        }
    }

    // --- Acciones sobre órdenes locales ---

    /**
     * Acepta una orden local del POS → PENDING → PREPARING.
     * Adicionalmente imprime el ticket Bluetooth.
     */
    fun acceptLocalOrder(order: Order, context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val printService = PrintService(context)
                    printService.printLocalOrderTicket(order)
                } catch (e: Exception) {
                    // La impresión es opcional — la orden avanza de todas formas
                }
            }
            orderRepository.acceptOrder(order.id)
        }
    }

    /** Cocina terminó → PREPARING → READY. La orden desaparece de la vista de cocina. */
    fun markLocalOrderReady(orderId: String) {
        viewModelScope.launch {
            orderRepository.markReady(orderId)
        }
    }

    /** Cliente/repartidor recoge → READY → DISPATCHED. */
    fun dispatchLocalOrder(orderId: String) {
        viewModelScope.launch {
            orderRepository.dispatch(orderId)
        }
    }

    // --- Acciones sobre órdenes del backend (Retrofit) ---

    fun acceptOrder(order: OrderRecord, context: Context) {
        viewModelScope.launch {
            try {
                val printSuccess = withContext(Dispatchers.IO) {
                    val printService = PrintService(context)
                    printService.printTicket(order)
                }

                if (printSuccess) {
                    println("🖨️ Impresión exitosa, enviando a cocina en base de datos...")
                } else {
                    println("⚠️ Falló la impresión, pero la orden avanzará a cocina de todos modos.")
                }

                val response = RetrofitClient.instance.acceptAndPrepareOrder(order.id)
                if (response.isSuccessful) {
                    fetchBackendOrders()
                }
            } catch (e: Exception) {
                println("⚠️ Error procesando la orden: ${e.message}")
            }
        }
    }

    fun completeOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.completeOrder(orderId)
                if (response.isSuccessful) {
                    fetchBackendOrders()
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
                    fetchBackendOrders()
                }
            } catch (e: Exception) {
                println("⚠️ Error validando pago: ${e.message}")
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KitchenViewModel(provideOrderRepository(context)) as T
                }
            }
    }
}
package com.restaurant.sushimei.frontend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.api.NetworkModule
import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.local.provideOrderRepository
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KitchenViewModel(
    private val orderRepository: IOrderRepository,
    private val api: SushiMeiApi = NetworkModule.sushiMeiApi,
    private val autoStartPolling: Boolean = true
) : ViewModel() {
    private val _operationalSummaries = MutableStateFlow<List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto>>(emptyList())

    val operationalSummaries: StateFlow<List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto>> = _operationalSummaries.asStateFlow()

    private val _orderDetailCache = MutableStateFlow<Map<Long, com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>>(emptyMap())

    val orderDetailCache: StateFlow<Map<Long, com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>> = _orderDetailCache.asStateFlow()

    private val _kitchenError = MutableStateFlow<String?>(null)

    val kitchenError: StateFlow<String?> = _kitchenError.asStateFlow()

    fun dismissError() {
        _kitchenError.value = null
    }

    val localOrders: StateFlow<List<Order>> = orderRepository.activeOrders

    init {
        if (autoStartPolling) {
            startPollingOrders()
        }
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
            val response = api.getOperationalActiveOrders()

            if (response.isSuccessful) {
                val summaries = response.body() ?: emptyList()

                _operationalSummaries.value = summaries

                updateDetailCache(summaries)
            }
        } catch (e: Exception) {
            // Silently fail if backend doesn't exist
        }
    }

    private suspend fun updateDetailCache(summaries: List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto>) {
        val currentCache = _orderDetailCache.value.toMutableMap()

        val activeIds = summaries.map { it.id }.toSet()

        currentCache.keys.retainAll(activeIds)

        for (summary in summaries) {
            val cached = currentCache[summary.id]

            if (cached == null || cached.status != summary.status) {
                try {
                    val detailResponse = api.getOperationalOrderDetail(summary.id)

                    if (detailResponse.isSuccessful && detailResponse.body() != null) {
                        currentCache[summary.id] = detailResponse.body()!!
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        _orderDetailCache.value = currentCache
    }

    private suspend fun forceRefreshDetail(orderId: Long) {
        try {
            val response = api.getOperationalOrderDetail(orderId)

            if (response.isSuccessful && response.body() != null) {
                val currentCache = _orderDetailCache.value.toMutableMap()

                currentCache[orderId] = response.body()!!

                _orderDetailCache.value = currentCache
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    // --- Acciones sobre órdenes locales ---

    fun acceptLocalOrder(order: Order, context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val printService = PrintService(context)

                    printService.printLocalOrderTicket(order)
                } catch (e: Exception) {
                }
            }

            orderRepository.acceptOrder(order.id)
        }
    }

    fun markLocalOrderReady(orderId: Long) {
        viewModelScope.launch {
            orderRepository.markReady(orderId)
        }
    }

    fun dispatchLocalOrder(orderId: Long) {
        viewModelScope.launch {
            orderRepository.dispatch(orderId)
        }
    }

    // --- Acciones sobre órdenes del backend (Retrofit) ---

    // Modern operational actions

    fun acceptOperationalOrder(orderId: Long, context: Context) {
        viewModelScope.launch {
            try {
                val response = api.acceptAndPrepareOrder(orderId)

                if (response.isSuccessful) {
                    forceRefreshDetail(orderId)
                    val detail = _orderDetailCache.value[orderId]
                    if (detail == null) {
                        fetchBackendOrders()
                        _kitchenError.value = "La orden se aceptó, pero no se pudo cargar el ticket para imprimir."
                        return@launch
                    }

                    val printed = try {
                        withContext(Dispatchers.IO) {
                            PrintService(context).printOperationalTicket(detail)
                        }
                    } catch (_: Exception) {
                        false
                    }
                    if (!printed) {
                        _kitchenError.value = "La orden se aceptó y pasó a Cocinando, pero el ticket no se pudo imprimir."
                    }
                    fetchBackendOrders()
                } else {
                    _kitchenError.value = "Error del servidor: Rechazo en operación (HTTP ${response.code()})"
                }
            } catch (e: ApiException) {
                _kitchenError.value = "Rechazo del servidor: ${e.message ?: "Acción inválida"}"
            } catch (e: java.io.IOException) {
                _kitchenError.value = "Error de red al aceptar orden."
            } catch (e: Exception) {
                _kitchenError.value = "Error inesperado al aceptar orden."
            }
        }
    }

    fun markOperationalOrderReady(orderId: Long) {
        viewModelScope.launch {
            try {
                val response = api.markOrderReady(orderId)

                if (response.isSuccessful) {
                    forceRefreshDetail(orderId)

                    fetchBackendOrders()
                } else {
                    _kitchenError.value = "Error del servidor: Rechazo en operación (HTTP ${response.code()})"
                }
            } catch (e: ApiException) {
                _kitchenError.value = "Rechazo del servidor: ${e.message ?: "Acción inválida"}"
            } catch (e: java.io.IOException) {
                _kitchenError.value = "Error de red al marcar como listo."
            } catch (e: Exception) {
                _kitchenError.value = "Error inesperado."
            }
        }
    }

    fun completeOperationalOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                val response = api.completeOrder(orderId)

                if (response.isSuccessful) {
                    forceRefreshDetail(orderId)

                    fetchBackendOrders()
                } else {
                    _kitchenError.value = "Error del servidor: Rechazo en operación (HTTP ${response.code()})"
                }
            } catch (e: ApiException) {
                _kitchenError.value = "Rechazo del servidor: ${e.message ?: "Acción inválida"}"
            } catch (e: java.io.IOException) {
                _kitchenError.value = "Error de red al despachar."
            } catch (e: Exception) {
                _kitchenError.value = "Error inesperado."
            }
        }
    }

    fun validatePaymentOperational(orderId: Long) {
        viewModelScope.launch {
            try {
                val response = api.validatePayment(orderId)

                if (response.isSuccessful) {
                    forceRefreshDetail(orderId)

                    fetchBackendOrders()
                } else {
                    _kitchenError.value = "Error del servidor: Rechazo en operación (HTTP ${response.code()})"
                }
            } catch (e: ApiException) {
                _kitchenError.value = "Rechazo del servidor: ${e.message ?: "Acción inválida"}"
            } catch (e: java.io.IOException) {
                _kitchenError.value = "Error de red al validar pago."
            } catch (e: Exception) {
                _kitchenError.value = "Error inesperado."
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

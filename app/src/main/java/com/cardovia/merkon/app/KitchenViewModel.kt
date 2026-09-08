package com.cardovia.merkon.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.NetworkModule
import com.cardovia.merkon.app.data.api.MerkonApi
import com.cardovia.merkon.app.data.local.provideOrderRepository
import com.cardovia.merkon.app.data.model.Order
import com.cardovia.merkon.app.data.repository.IOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KitchenViewModel(
    private val orderRepository: IOrderRepository,
    private val api: MerkonApi = NetworkModule.merkonApi,
    private val autoStartPolling: Boolean = true,
    private val operationalOrderRepository: com.cardovia.merkon.app.data.repository.IOperationalOrderRepository? = null
) : ViewModel() {
    private val _operationalSummaries = MutableStateFlow<List<com.cardovia.merkon.app.data.model.OperationalOrderSummaryDto>>(emptyList())

    val operationalSummaries: StateFlow<List<com.cardovia.merkon.app.data.model.OperationalOrderSummaryDto>> = _operationalSummaries.asStateFlow()

    private val _orderDetailCache = MutableStateFlow<Map<Long, com.cardovia.merkon.app.data.model.OperationalOrderDetailDto>>(emptyMap())

    val orderDetailCache: StateFlow<Map<Long, com.cardovia.merkon.app.data.model.OperationalOrderDetailDto>> = _orderDetailCache.asStateFlow()

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

    internal suspend fun fetchBackendOrders() {
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

    private suspend fun updateDetailCache(summaries: List<com.cardovia.merkon.app.data.model.OperationalOrderSummaryDto>) {
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

    private val _collectionInFlightOrderId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val collectionInFlightOrderId: kotlinx.coroutines.flow.StateFlow<Long?> = _collectionInFlightOrderId.asStateFlow()

    private val _collectionConfirmationOrderId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val collectionConfirmationOrderId: kotlinx.coroutines.flow.StateFlow<Long?> = _collectionConfirmationOrderId.asStateFlow()

    private val _collectionError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val collectionError: kotlinx.coroutines.flow.StateFlow<String?> = _collectionError.asStateFlow()

    private val _collectionSuccessMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val collectionSuccessMessage: kotlinx.coroutines.flow.StateFlow<String?> = _collectionSuccessMessage.asStateFlow()

    private var activeOrderSessionGeneration = 0

    fun openCollectionConfirmation(orderId: Long) {
        _collectionConfirmationOrderId.value = orderId
        _collectionError.value = null
        _collectionSuccessMessage.value = null
    }

    fun closeCollectionConfirmation() {
        if (_collectionInFlightOrderId.value != null) return
        _collectionConfirmationOrderId.value = null
        _collectionError.value = null
        _collectionSuccessMessage.value = null
        activeOrderSessionGeneration++
    }

    fun clearCollectionState() {
        _collectionConfirmationOrderId.value = null
        _collectionError.value = null
        _collectionSuccessMessage.value = null
        activeOrderSessionGeneration++
    }

    fun submitCollection(orderId: Long, method: com.cardovia.merkon.app.data.model.PaymentMethod, denomination: java.math.BigDecimal?) {
        val repo = operationalOrderRepository ?: return
        if (_collectionInFlightOrderId.value != null) return

        val order = _operationalSummaries.value.find { it.id == orderId } ?: run {
            _collectionError.value = "La orden no está disponible para cobro."
            return
        }

        val validated = try {
            com.cardovia.merkon.app.ui.shared.CollectionOrchestrator.validateCollection(
                orderStatus = order.status,
                requiresPaymentCollection = order.requiresPaymentCollection,
                orderTotal = order.total,
                method = method,
                denomination = denomination
            )
        } catch (e: IllegalArgumentException) {
            _collectionError.value = e.message
            return
        }

        val currentSession = activeOrderSessionGeneration
        _collectionInFlightOrderId.value = orderId
        _collectionError.value = null

        viewModelScope.launch {
            try {
                repo.collectPayment(orderId, validated.method, validated.denomination)
                if (currentSession != activeOrderSessionGeneration) return@launch

                _collectionConfirmationOrderId.value = null
                _collectionInFlightOrderId.value = null
                _collectionSuccessMessage.value = "Cobro registrado correctamente."
                fetchBackendOrders()
            } catch (e: com.cardovia.merkon.app.data.api.ApiException) {
                if (currentSession != activeOrderSessionGeneration) return@launch
                _collectionInFlightOrderId.value = null
                _collectionError.value = com.cardovia.merkon.app.ui.shared.CollectionOrchestrator.mapCollectionApiError(e)
            } catch (e: java.io.IOException) {
                if (currentSession != activeOrderSessionGeneration) return@launch
                try {
                    val outcome = com.cardovia.merkon.app.ui.shared.CollectionOrchestrator.executeReconciliation(repo, orderId)
                    if (outcome is com.cardovia.merkon.app.ui.shared.CollectionOrchestrator.ReconciliationOutcome.Success) {
                        _collectionConfirmationOrderId.value = null
                        _collectionInFlightOrderId.value = null
                        _collectionSuccessMessage.value = "Cobro registrado correctamente."
                        fetchBackendOrders()
                    } else {
                        val msg = when (outcome) {
                            is com.cardovia.merkon.app.ui.shared.CollectionOrchestrator.ReconciliationOutcome.Retryable -> outcome.message
                            is com.cardovia.merkon.app.ui.shared.CollectionOrchestrator.ReconciliationOutcome.Uncertain -> outcome.message
                            else -> "Fallo desconocido."
                        }
                        if (currentSession != activeOrderSessionGeneration) return@launch
                        _collectionInFlightOrderId.value = null
                        _collectionError.value = msg
                    }
                } catch (fallback: Exception) {
                    if (currentSession != activeOrderSessionGeneration) return@launch
                    _collectionInFlightOrderId.value = null
                    _collectionError.value = "Fallo de red. El estado del cobro es incierto. Refresca las órdenes antes de intentar de nuevo."
                }
            } catch (e: Exception) {
                if (currentSession != activeOrderSessionGeneration) return@launch
                _collectionInFlightOrderId.value = null
                _collectionError.value = "Error inesperado: ${e.message}"
            }
        }
    }


    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KitchenViewModel(
                        provideOrderRepository(context),
                        api = NetworkModule.merkonApi,
                        operationalOrderRepository = com.cardovia.merkon.app.data.repository.RemoteOperationalOrderRepository(NetworkModule.merkonApi)
                    ) as T
                }
            }
    }
}

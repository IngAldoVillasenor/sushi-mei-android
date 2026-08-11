package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

/**
 * Implementación en memoria de [IOrderRepository].
 *
 * Diseñado como singleton (`object`) para que [PosViewModel] y [KitchenViewModel]
 * compartan exactamente la misma instancia sin necesidad de Hilt ni de pasar
 * referencias entre Composables.
 *
 * Thread-safety: las mutaciones se realizan siempre en corrutinas. Dado que
 * kotlinx-coroutines serializa las corrutinas en el mismo dispatcher, el
 * acceso a [_allOrders] es seguro para este contexto de uso.
 *
 * Sustitución futura: crear `ApiOrderRepository : IOrderRepository` que use
 * Retrofit y reemplazar `MockOrderRepository` en los factories de los ViewModels.
 */
object MockOrderRepository : IOrderRepository {

    private val _allOrders = MutableStateFlow<List<Order>>(emptyList())

    /**
     * Solo expone PENDING, PREPARING y READY.
     * Las órdenes DISPATCHED desaparecen de la UI automáticamente.
     */
    override val activeOrders: StateFlow<List<Order>>
        get() = MutableStateFlow(
            _allOrders.value.filter { it.status != OrderStatus.DISPATCHED }
        ).also { updateActiveOrders() }

    // StateFlow real que la UI observa
    private val _activeOrders = MutableStateFlow<List<Order>>(emptyList())
    val activeOrdersFlow: StateFlow<List<Order>> = _activeOrders.asStateFlow()

    override suspend fun placeOrder(items: List<ConfiguredProduct>, total: java.math.BigDecimal) {
        val newOrder = Order(
            id = System.currentTimeMillis(),
            items = items,
            total = total,
            status = OrderStatus.PENDING
        )
        _allOrders.value = _allOrders.value + newOrder
        updateActiveOrders()
    }

    override suspend fun acceptOrder(orderId: Long) =
        updateStatus(orderId, OrderStatus.PENDING, OrderStatus.PREPARING)

    override suspend fun markReady(orderId: Long) =
        updateStatus(orderId, OrderStatus.PREPARING, OrderStatus.READY)

    override suspend fun dispatch(orderId: Long) {
        _allOrders.value = _allOrders.value.map { order ->
            if (order.id == orderId && order.status == OrderStatus.READY) {
                order.copy(status = OrderStatus.DISPATCHED)
            } else order
        }
        updateActiveOrders()
    }

    private fun updateStatus(orderId: Long, from: OrderStatus, to: OrderStatus) {
        _allOrders.value = _allOrders.value.map { order ->
            if (order.id == orderId && order.status == from) order.copy(status = to)
            else order
        }
        updateActiveOrders()
    }

    private fun updateActiveOrders() {
        _activeOrders.value = _allOrders.value.filter { it.status != OrderStatus.DISPATCHED }
    }

    /** Solo para tests: resetea el estado interno. */
    fun reset() {
        _allOrders.value = emptyList()
        _activeOrders.value = emptyList()
    }

    override fun observeDispatchedToday(): Flow<List<Order>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return _allOrders.map { orders ->
            orders.filter { it.status == OrderStatus.DISPATCHED && it.createdAt >= startOfDay }
        }
    }
}

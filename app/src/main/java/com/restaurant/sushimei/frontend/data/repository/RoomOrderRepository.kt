package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.local.ConfiguredProductTypeConverter
import com.restaurant.sushimei.frontend.data.local.OrderDao
import com.restaurant.sushimei.frontend.data.local.OrderEntity
import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.model.OrderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.UUID

/**
 * Implementación de [IOrderRepository] respaldada por Room SQLite.
 *
 * Las órdenes persisten entre sesiones de la app: si el dispositivo se apaga
 * mientras hay órdenes PENDING o PREPARING en cocina, estas sobreviven al
 * reinicio y vuelven a aparecer automáticamente en [KitchenScreen].
 *
 * El [StateFlow] de [activeOrders] es reactivo: Room emite nuevas listas
 * cada vez que la tabla cambia, sin necesidad de polling.
 *
 * Sustitución futura: crear [ApiOrderRepository] para sincronizar con
 * el backend Spring Boot cuando los endpoints estén disponibles.
 */
class RoomOrderRepository(private val dao: OrderDao) : IOrderRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val activeOrders: StateFlow<List<Order>> =
        dao.observeActiveOrders()
            .map { entities -> entities.map { it.toDomain() } }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    override suspend fun placeOrder(items: List<ConfiguredProduct>, total: java.math.BigDecimal) {
        val entity = OrderEntity(
            itemsJson  = ConfiguredProductTypeConverter.fromList(items),
            total      = total,
            createdAt  = System.currentTimeMillis(),
            status     = OrderStatus.PENDING.name
        )
        dao.insert(entity)
    }

    override suspend fun acceptOrder(orderId: Long) =
        dao.updateStatus(orderId, OrderStatus.PREPARING.name)

    override suspend fun markReady(orderId: Long) =
        dao.updateStatus(orderId, OrderStatus.READY.name)

    override suspend fun dispatch(orderId: Long) =
        dao.updateStatus(orderId, OrderStatus.DISPATCHED.name)

    override fun observeDispatchedToday(): Flow<List<Order>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return dao.observeDispatched(startOfDay)
            .map { entities -> entities.map { it.toDomain() } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mappers: OrderEntity ↔ Order
// ─────────────────────────────────────────────────────────────────────────────


private fun OrderEntity.toDomain(): Order = Order(
    id        = id,
    items     = ConfiguredProductTypeConverter.toList(itemsJson),
    total     = total,
    createdAt = createdAt,
    status    = OrderStatus.valueOf(status)
)

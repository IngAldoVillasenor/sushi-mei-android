package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato del repositorio de órdenes.
 *
 * Implementación actual: [RoomOrderRepository] (Room SQLite).
 * Implementación futura: ApiOrderRepository (Retrofit → Spring Boot).
 */
interface IOrderRepository {

    /**
     * Lista reactiva de órdenes activas (PENDING, PREPARING y READY).
     * Las órdenes DISPATCHED se excluyen automáticamente.
     */
    val activeOrders: StateFlow<List<Order>>

    /** Crea una nueva orden a partir del carrito cobrado en el POS. PENDING. */
    suspend fun placeOrder(items: List<ConfiguredProduct>, total: java.math.BigDecimal)

    /** Cocina acepta la orden → PENDING → PREPARING. */
    suspend fun acceptOrder(orderId: String)

    /** Cocina termina la orden → PREPARING → READY. Desaparece de la vista de cocina. */
    suspend fun markReady(orderId: String)

    /** Cliente/repartidor recoge → READY → DISPATCHED. Se elimina de la lista activa. */
    suspend fun dispatch(orderId: String)

    /**
     * Flow reactivo de órdenes DISPATCHED desde el inicio del día actual.
     * Se actualiza automáticamente cada vez que se despacha una orden nueva.
     * Usado por el Dashboard para calcular métricas en tiempo real.
     */
    fun observeDispatchedToday(): Flow<List<Order>>
}


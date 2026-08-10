package com.restaurant.sushimei.frontend.data.model

/**
 * Modelo de dominio local para una orden generada desde el POS.
 *
 * Independiente de [OrderRecord], que está acoplado al backend.
 * Cuando el backend exista, se creará un mapper entre ambos.
 */
data class Order(
    val id: String,                // UUID generado en el cliente
    val items: List<ConfiguredProduct>,     // Snapshot inmutable del carrito al momento de cobrar
    val total: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val status: OrderStatus = OrderStatus.PENDING
)

enum class OrderStatus {
    /** Recién cobrada en el POS, esperando que cocina la acepte. */
    PENDING,

    /** Cocina aceptó la orden y está preparándola. */
    PREPARING,

    /** Lista para entregar — ya no está en la vista de cocina, espera al cliente/repartidor. */
    READY,

    /** El cliente o repartidor se llevó el pedido. Flujo completado. */
    DISPATCHED
}

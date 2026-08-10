package com.restaurant.sushimei.frontend.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que persiste una orden en SQLite.
 *
 * Separada del modelo de dominio [com.restaurant.sushimei.frontend.data.model.Order]
 * para mantener las anotaciones de Android fuera del dominio.
 *
 * Los ítems del carrito se almacenan como JSON serializado en [itemsJson],
 * evitando una tabla de relación innecesaria en esta etapa del proyecto.
 * Cuando el backend exista, el mapper convierte este JSON a la estructura de la API.
 */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: String,

    /** Lista de ConfiguredProduct serializada como JSON. Ver [ConfiguredProductTypeConverter]. */
    val itemsJson: String,

    val total: java.math.BigDecimal,
    val createdAt: Long,

    /** Nombre del enum [com.restaurant.sushimei.frontend.data.model.OrderStatus]. */
    val status: String
)

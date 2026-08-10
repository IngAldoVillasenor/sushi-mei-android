package com.restaurant.sushimei.frontend.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que representa un producto del menú en SQLite.
 *
 * Separada del modelo de dominio [com.restaurant.sushimei.frontend.data.model.MenuItem]
 * para mantener las anotaciones de Android fuera del dominio.
 *
 * Los 121 productos se cargan desde assets/menu.json la primera vez que
 * se abre la app (seed automático en [RoomMenuRepository]).
 * A partir de ahí, toda edición persiste en SQLite y no toca el JSON.
 *
 * Nota API-First: cuando el backend exista, los precios serán la fuente de
 * verdad del servidor. Los cambios locales son válidos solo en modo offline/mock.
 */
@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double,
    val descripcion: String = "",
    val emoji: String = "🍣",

    /**
     * Si false, el producto no aparece en el POS ni puede agregarse al carrito.
     * Útil para productos de temporada o agotados.
     */
    val activo: Boolean = true
)

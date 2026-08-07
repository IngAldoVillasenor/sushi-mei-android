package com.restaurant.sushimei.frontend.data.model

data class MenuItem(
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double,
    val descripcion: String = "",
    val emoji: String = "🍣"
)

data class CartItem(
    val menuItem: MenuItem,
    val cantidad: Int
) {
    val subtotal: Double
        get() = menuItem.precio * cantidad
}

package com.restaurant.sushimei.frontend.ui.pos

import androidx.lifecycle.ViewModel
import com.restaurant.sushimei.frontend.data.model.CartItem
import com.restaurant.sushimei.frontend.data.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PosViewModel : ViewModel() {

    val menuItems: List<MenuItem> = listOf(
        MenuItem(
            id = "1",
            nombre = "Empanizado Ebi",
            categoria = "Clásicos",
            precio = 135.0,
            descripcion = "Camarón, queso crema y empanizado crujiente",
            emoji = "🍤"
        ),
        MenuItem(
            id = "2",
            nombre = "Roll California",
            categoria = "Clásicos",
            precio = 95.0,
            descripcion = "Surimi, pepino, aguacate y ajonjolí",
            emoji = "🥑"
        ),
        MenuItem(
            id = "3",
            nombre = "Roll Philadelphia",
            categoria = "Clásicos",
            precio = 110.0,
            descripcion = "Salmón fresco, queso crema y ajonjolí",
            emoji = "🍣"
        ),
        MenuItem(
            id = "4",
            nombre = "Fuego Manchego",
            categoria = "Especiales",
            precio = 165.0,
            descripcion = "Roll gratinado con queso manchego y serrano",
            emoji = "🔥"
        ),
        MenuItem(
            id = "5",
            nombre = "Charola Chica",
            categoria = "Especiales",
            precio = 320.0,
            descripcion = "Combinación de 3 rolls a elegir + edamames",
            emoji = "🍱"
        ),
        MenuItem(
            id = "6",
            nombre = "Dragon Roll",
            categoria = "Especiales",
            precio = 175.0,
            descripcion = "Anguila, cobertura de aguacate y salsa unagi",
            emoji = "🐉"
        ),
        MenuItem(
            id = "7",
            nombre = "Coca Cola 600ml",
            categoria = "Bebidas",
            precio = 35.0,
            descripcion = "Refresco embotellado frío",
            emoji = "🥤"
        ),
        MenuItem(
            id = "8",
            nombre = "Té Helado Jasmine",
            categoria = "Bebidas",
            precio = 40.0,
            descripcion = "Té verde preparado en casa con toque de limón",
            emoji = "🍵"
        )
    )

    val categories: List<String> = listOf("Todos", "Clásicos", "Especiales", "Bebidas")

    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _currentCart = MutableStateFlow<List<CartItem>>(emptyList())
    val currentCart: StateFlow<List<CartItem>> = _currentCart.asStateFlow()

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addToCart(menuItem: MenuItem) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.menuItem.id == menuItem.id }

        if (index >= 0) {
            val existing = currentList[index]
            currentList[index] = existing.copy(cantidad = existing.cantidad + 1)
        } else {
            currentList.add(CartItem(menuItem = menuItem, cantidad = 1))
        }

        _currentCart.value = currentList
    }

    fun removeFromCart(cartItem: CartItem) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.menuItem.id == cartItem.menuItem.id }

        if (index >= 0) {
            val existing = currentList[index]
            if (existing.cantidad > 1) {
                currentList[index] = existing.copy(cantidad = existing.cantidad - 1)
            } else {
                currentList.removeAt(index)
            }
            _currentCart.value = currentList
        }
    }

    fun deleteFromCart(cartItem: CartItem) {
        val currentList = _currentCart.value.toMutableList()
        currentList.removeAll { it.menuItem.id == cartItem.menuItem.id }
        _currentCart.value = currentList
    }

    fun clearCart() {
        _currentCart.value = emptyList()
    }

    fun getTotal(): Double {
        return _currentCart.value.sumOf { it.subtotal }
    }
}

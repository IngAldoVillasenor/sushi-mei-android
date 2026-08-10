package com.restaurant.sushimei.frontend.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.restaurant.sushimei.frontend.data.model.OrderPricingPreview
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository

/**
 * ViewModel del módulo Punto de Venta.
 *
 * Gestiona el catálogo de productos (cargado desde [IMenuRepository]),
 * el filtrado por categoría y el estado del carrito de compras.
 *
 * Al cobrar, delega en [IOrderRepository] para publicar la orden al módulo
 * al módulo de Cocina a través del singleton compartido [MockOrderRepository].
 */
sealed interface PosUiState {
    object Loading : PosUiState
    data class Success(
        val categories: List<String> = listOf("Todos"),
        val selectedCategory: String? = null,
        val filteredProducts: List<MenuItem> = emptyList(),
        val currentCart: List<ConfiguredProduct> = emptyList(),
        val pricingPreview: OrderPricingPreview = OrderPricingPreview(java.math.BigDecimal.ZERO, emptyList(), emptyList(), java.math.BigDecimal.ZERO)
    ) : PosUiState
}

class PosViewModel(
    private val menuRepository: IMenuRepository,
    private val orderRepository: IOrderRepository,
    private val promotionRepository: IPromotionRepository
) : ViewModel() {

    // --- Estado interno ---
    private val _allProducts = MutableStateFlow<List<MenuItem>>(emptyList())
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _currentCart = MutableStateFlow<List<ConfiguredProduct>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _pricingPreview = MutableStateFlow(OrderPricingPreview(java.math.BigDecimal.ZERO, emptyList(), emptyList(), java.math.BigDecimal.ZERO))

    // --- Estado público expuesto a la UI ---
    val uiState: StateFlow<PosUiState> = combine(
        _allProducts,
        _selectedCategory,
        _currentCart,
        _isLoading,
        _pricingPreview
    ) { all, category, cart, loading, preview ->
        if (loading) {
            PosUiState.Loading
        } else {
            val categories = buildList {
                add("Todos")
                addAll(all.map { it.categoria }.distinct().sorted())
            }
            val filtered = if (category == null || category == "Todos") {
                all
            } else {
                all.filter { it.categoria == category }
            }
            PosUiState.Success(
                categories = categories,
                selectedCategory = category,
                filteredProducts = filtered,
                currentCart = cart,
                pricingPreview = preview
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PosUiState.Loading)

    init {
        // Observa el Flow reactivo de Room — el catálogo del POS se actualiza
        // automáticamente cuando el dueño edita un producto en Gestión de Menú.
        viewModelScope.launch {
            _isLoading.value = true
            menuRepository.observeActive().collect { products ->
                _allProducts.value = products
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            _currentCart.collect { cart ->
                val preview = promotionRepository.quoteCart(cart)
                _pricingPreview.value = preview
            }
        }
    }

    // --- Acciones de categoría ---

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    // --- Acciones del carrito ---

    fun addToCart(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                // FASE 6A2: Do not derive price locally. Quote it.
                val quoteRequest = com.restaurant.sushimei.frontend.data.model.QuoteRequestDto(
                    quantity = 1,
                    groups = emptyList()
                )
                val quote = menuRepository.quoteItem(menuItem.id, quoteRequest)
                
                val product = ConfiguredProduct(
                    menuItemId = quote.menuItemId,
                    name = quote.name,
                    quantity = quote.quantity,
                    baseUnitPrice = quote.baseUnitPrice,
                    unitTotal = quote.unitTotal,
                    total = quote.total,
                    groups = emptyList()
                )
                
                // Add to internal list
                val currentList = _currentCart.value.toMutableList()
                val index = currentList.indexOfFirst { it.menuItemId == product.menuItemId && it.groups.isEmpty() }

                if (index >= 0) {
                    val existing = currentList[index]
                    currentList[index] = existing.copy(
                        quantity = existing.quantity + product.quantity,
                        total = existing.unitTotal * java.math.BigDecimal(existing.quantity + product.quantity)
                    )
                } else {
                    currentList.add(product)
                }
                
                _currentCart.value = currentList
            } catch (e: Exception) {
                // handle error / log error
            }
        }
    }

    fun addConfiguredProduct(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        // Here we could try to merge identical configurations, but for now we'll just add it as a new line item.
        // Or we compare if they have the same configuration. Since it's complex, we just add it.
        // Wait, it's better to check if an exact match exists.
        val index = currentList.indexOfFirst { 
            it.menuItemId == configuredProduct.menuItemId && it.groups == configuredProduct.groups 
        }

        if (index >= 0) {
            val existing = currentList[index]
            currentList[index] = existing.copy(
                quantity = existing.quantity + configuredProduct.quantity,
                total = existing.unitTotal * java.math.BigDecimal(existing.quantity + configuredProduct.quantity)
            )
        } else {
            currentList.add(configuredProduct)
        }

        _currentCart.value = currentList
    }

    fun removeFromCart(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == configuredProduct.id }

        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity > 1) {
                currentList[index] = existing.copy(
                    quantity = existing.quantity - 1,
                    total = existing.unitTotal * java.math.BigDecimal(existing.quantity - 1)
                )
            } else {
                currentList.removeAt(index)
            }
            _currentCart.value = currentList
        }
    }

    fun deleteFromCart(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        currentList.removeAll { it.id == configuredProduct.id }
        _currentCart.value = currentList
    }

    fun clearCart() {
        _currentCart.value = emptyList()
    }

    /**
     * Cierra la orden actual: la publica en [IOrderRepository] (visible para Cocina)
     * y luego limpia el carrito.
     */
    fun cobrarOrden() {
        val items = _currentCart.value
        if (items.isEmpty()) return
        val total = _pricingPreview.value.total
        viewModelScope.launch {
            orderRepository.placeOrder(items, total)
            clearCart()
        }
    }

    fun getTotal(): java.math.BigDecimal {
        return _pricingPreview.value.total
    }

    // --- Factory para creación manual (sin Hilt) ---

    companion object {
        fun factory(
            menuRepository: IMenuRepository,
            orderRepository: IOrderRepository,
            promotionRepository: IPromotionRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PosViewModel(menuRepository, orderRepository, promotionRepository) as T
                }
            }
    }
}

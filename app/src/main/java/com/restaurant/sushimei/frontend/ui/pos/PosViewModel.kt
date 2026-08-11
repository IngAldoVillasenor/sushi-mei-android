package com.restaurant.sushimei.frontend.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.model.*
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

sealed interface QuoteState {
    object Idle : QuoteState
    object Loading : QuoteState
    data class Valid(val preview: OrderPricingPreview) : QuoteState
    data class Error(val message: String) : QuoteState
}

sealed interface CheckoutState {
    object Idle : CheckoutState
    object Loading : CheckoutState
    data class Success(val response: ManualPosOrderResponse) : CheckoutState
    data class Error(val message: String) : CheckoutState
}

sealed interface PosUiState {
    object Loading : PosUiState
    data class Success(
        val categories: List<String> = listOf("Todos"),
        val selectedCategory: String? = null,
        val filteredProducts: List<MenuItem> = emptyList(),
        val currentCart: List<ConfiguredProduct> = emptyList(),
        val quoteState: QuoteState = QuoteState.Idle,
        val checkoutState: CheckoutState = CheckoutState.Idle,
        val fulfillmentType: FulfillmentType = FulfillmentType.PICKUP,
        val paymentMethod: PaymentMethod = PaymentMethod.CASH,
        val pickupName: String? = "",
        val deliveryAddress: String? = "",
        val cashDenomination: BigDecimal? = null
    ) : PosUiState
}

class PosViewModel(
    private val menuRepository: IMenuRepository,
    private val manualPosOrderRepository: IManualPosOrderRepository,
    private val promotionRepository: IPromotionRepository
) : ViewModel() {

    // --- Estado interno ---
    private val _allProducts = MutableStateFlow<List<MenuItem>>(emptyList())
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _currentCart = MutableStateFlow<List<ConfiguredProduct>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _quoteState = MutableStateFlow<QuoteState>(QuoteState.Idle)
    private val _checkoutState = MutableStateFlow<CheckoutState>(CheckoutState.Idle)

    private val _fulfillmentType = MutableStateFlow(FulfillmentType.PICKUP)
    private val _paymentMethod = MutableStateFlow(PaymentMethod.CASH)
    private val _pickupName = MutableStateFlow<String?>("")
    private val _deliveryAddress = MutableStateFlow<String?>("")
    private val _cashDenomination = MutableStateFlow<BigDecimal?>(null)

    // Idempotency
    private var pendingRequestId: UUID? = null

    // --- Estado público expuesto a la UI ---
    val uiState: StateFlow<PosUiState> = combine(
        combine(_allProducts, _selectedCategory, _currentCart, ::Triple),
        combine(_isLoading, _quoteState, _checkoutState, ::Triple),
        combine(_fulfillmentType, _paymentMethod, _pickupName, _deliveryAddress, _cashDenomination) { f, p, pn, da, cd ->
            MetadataState(f, p, pn, da, cd)
        }
    ) { (all, category, cart), (loading, quote, checkout), metadata ->
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
                quoteState = quote,
                checkoutState = checkout,
                fulfillmentType = metadata.fulfillmentType,
                paymentMethod = metadata.paymentMethod,
                pickupName = metadata.pickupName,
                deliveryAddress = metadata.deliveryAddress,
                cashDenomination = metadata.cashDenomination
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PosUiState.Loading)

    private data class MetadataState(
        val fulfillmentType: FulfillmentType,
        val paymentMethod: PaymentMethod,
        val pickupName: String?,
        val deliveryAddress: String?,
        val cashDenomination: BigDecimal?
    )

    init {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                menuRepository.refreshCatalog(standaloneOnly = true)
            } catch (e: Exception) {
                // handle error
            }
            menuRepository.observeActive().collect { products ->
                _allProducts.value = products
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            _currentCart.collect { cart ->
                if (cart.isEmpty()) {
                    _quoteState.value = QuoteState.Idle
                    return@collect
                }
                _quoteState.value = QuoteState.Loading
                try {
                    val preview = promotionRepository.quoteCart(cart)
                    _quoteState.value = QuoteState.Valid(preview)
                } catch (e: Exception) {
                    _quoteState.value = QuoteState.Error("Error al cotizar orden: ${e.message}")
                }
            }
        }
    }

    private fun invalidateRequestId() {
        pendingRequestId = null
    }

    // --- Acciones de metadatos ---
    fun updateFulfillmentType(type: FulfillmentType) {
        if (_fulfillmentType.value != type) {
            _fulfillmentType.value = type
            invalidateRequestId()
        }
    }

    fun updatePaymentMethod(method: PaymentMethod) {
        if (_paymentMethod.value != method) {
            _paymentMethod.value = method
            invalidateRequestId()
        }
    }

    fun updatePickupName(name: String) {
        val oldEffective = _pickupName.value?.trim()
        val newEffective = name.trim()
        _pickupName.value = name
        if (oldEffective != newEffective) {
            invalidateRequestId()
        }
    }

    fun updateDeliveryAddress(address: String) {
        val oldEffective = _deliveryAddress.value?.trim()
        val newEffective = address.trim()
        _deliveryAddress.value = address
        if (oldEffective != newEffective) {
            invalidateRequestId()
        }
    }

    fun updateCashDenomination(denom: BigDecimal?) {
        val old = _cashDenomination.value
        val equal = if (old == null && denom == null) true
            else if (old != null && denom != null) old.compareTo(denom) == 0
            else false

        _cashDenomination.value = denom
        if (!equal) {
            invalidateRequestId()
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addToCart(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                val quoteRequest = ItemQuoteRequestDto(
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

                val currentList = _currentCart.value.toMutableList()
                val index = currentList.indexOfFirst { it.menuItemId == product.menuItemId && it.groups.isEmpty() }

                if (index >= 0) {
                    val existing = currentList[index]
                    currentList[index] = existing.copy(
                        quantity = existing.quantity + product.quantity,
                        total = existing.unitTotal * BigDecimal(existing.quantity + product.quantity)
                    )
                } else {
                    currentList.add(product)
                }

                _currentCart.value = currentList
                invalidateRequestId()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun addConfiguredProduct(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst {
            it.menuItemId == configuredProduct.menuItemId && it.groups == configuredProduct.groups
        }

        if (index >= 0) {
            val existing = currentList[index]
            currentList[index] = existing.copy(
                quantity = existing.quantity + configuredProduct.quantity,
                total = existing.unitTotal * BigDecimal(existing.quantity + configuredProduct.quantity)
            )
        } else {
            currentList.add(configuredProduct)
        }

        _currentCart.value = currentList
        invalidateRequestId()
    }

    fun removeFromCart(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == configuredProduct.id }

        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity > 1) {
                currentList[index] = existing.copy(
                    quantity = existing.quantity - 1,
                    total = existing.unitTotal * BigDecimal(existing.quantity - 1)
                )
            } else {
                currentList.removeAt(index)
            }
            _currentCart.value = currentList
            invalidateRequestId()
        }
    }

    fun deleteFromCart(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        val removed = currentList.removeAll { it.id == configuredProduct.id }
        if (removed) {
            _currentCart.value = currentList
            invalidateRequestId()
        }
    }

    fun clearCart() {
        if (_currentCart.value.isNotEmpty()) {
            _currentCart.value = emptyList()
            invalidateRequestId()
        }
    }

    private fun validateCheckout(
        fulfillment: FulfillmentType,
        payment: PaymentMethod,
        pickup: String?,
        address: String?,
        denomination: BigDecimal?
    ): String? {
        if (fulfillment == FulfillmentType.PICKUP) {
            val trimmedName = pickup?.trim()
            if (trimmedName.isNullOrEmpty() || trimmedName.length < 2 || trimmedName.length > 120) {
                return "El nombre para recoger debe tener entre 2 y 120 caracteres."
            }
        } else if (fulfillment == FulfillmentType.DELIVERY) {
            val trimmedAddress = address?.trim()
            if (trimmedAddress.isNullOrEmpty() || trimmedAddress.length < 5 || trimmedAddress.length > 500) {
                return "La dirección de entrega debe tener entre 5 y 500 caracteres."
            }
            if (payment == PaymentMethod.CARD) {
                return "El pago con tarjeta solo está disponible para pedidos a recoger."
            }
        }

        if (payment == PaymentMethod.CASH) {
            if (denomination == null || denomination <= BigDecimal.ZERO) {
                return "Debes ingresar una denominación válida mayor a cero."
            }
        }
        return null
    }

    fun cobrarOrden() {
        val items = _currentCart.value

        // Synchronous guard against duplicate submit or empty cart
        if (items.isEmpty() || _checkoutState.value == CheckoutState.Loading) return

        // Logical snapshot of the checkout metadata
        val fulfillment = _fulfillmentType.value
        val payment = _paymentMethod.value
        val pickup = _pickupName.value?.trim()
        val address = _deliveryAddress.value?.trim()
        val denomination = _cashDenomination.value

        val validationError = validateCheckout(fulfillment, payment, pickup, address, denomination)
        if (validationError != null) {
            _checkoutState.value = CheckoutState.Error(validationError)
            return
        }

        // Set Loading synchronously so no other call can enter
        _checkoutState.value = CheckoutState.Loading

        if (pendingRequestId == null) {
            pendingRequestId = UUID.randomUUID()
        }

        val requestSnapshot = ManualPosOrderRequest(
            requestId = pendingRequestId.toString(),
            fulfillmentType = fulfillment,
            paymentMethod = payment,
            deliveryAddress = if (fulfillment == FulfillmentType.DELIVERY) address else null,
            pickupName = if (fulfillment == FulfillmentType.PICKUP) pickup else null,
            cashDenomination = if (payment == PaymentMethod.CASH) denomination else null,
            lines = items.map { buildRequestLine(it) }
        )

        viewModelScope.launch {
            try {
                val response = manualPosOrderRepository.submitOrder(requestSnapshot)
                if (response.result == OrderResult.CREATED || response.result == OrderResult.ALREADY_CREATED) {
                    clearCart()
                    invalidateRequestId()
                    _checkoutState.value = CheckoutState.Success(response)
                } else {
                    _checkoutState.value = CheckoutState.Error("Respuesta inesperada del servidor.")
                }
            } catch (e: ApiException) {
                _checkoutState.value = CheckoutState.Error(mapApiError(e))
            } catch (e: Exception) {
                _checkoutState.value = CheckoutState.Error("Error de red: La orden no pudo confirmarse. Intenta de nuevo.")
                // No invalidamos el requestId para permitir reintento seguro
            }
        }
    }

    private fun mapApiError(e: ApiException): String {
        return when (e.code) {
            "ORDER_INVALID" -> "Datos de orden inválidos. Revisa la información."
            "ORDER_IDEMPOTENCY_CONFLICT" -> "Conflicto de idempotencia. Esta orden puede haber sido procesada parcialmente."
            "ORDER_MENU_ITEM_NOT_FOUND" -> "Un producto ya no existe en el catálogo."
            "ORDER_MENU_ITEM_UNAVAILABLE" -> "Un producto seleccionado no está disponible."
            "ORDER_CONFIGURATION_INVALID" -> "Configuración de producto inválida."
            "ORDER_PROMOTION_CONFLICT" -> "Conflicto de promoción. Los precios pudieron haber cambiado."
            "ORDER_FORBIDDEN_OPERATION", "AUTH_FORBIDDEN" -> "No tienes permisos para realizar esta operación."
            else -> "Error del servidor. La orden no pudo confirmarse. Intenta de nuevo."
        }
    }

    private fun buildRequestLine(product: ConfiguredProduct): PosOrderRequestLineDto {
        return PosOrderRequestLineDto(
            lineKey = product.id,
            menuItemId = product.menuItemId,
            quantity = product.quantity,
            groups = product.groups.map { buildRequestGroup(it) },
            rewardConfigurations = emptyList() // Manual checkout never infers rewards
        )
    }

    private fun buildRequestGroup(group: ConfiguredGroup): QuoteRequestGroupDto {
        return QuoteRequestGroupDto(
            groupId = group.groupId,
            selections = group.selections.map { buildRequestSelection(it) }
        )
    }

    private fun buildRequestSelection(selection: ConfiguredSelection): QuoteRequestSelectionDto {
        return QuoteRequestSelectionDto(
            menuItemId = selection.menuItemId,
            quantity = selection.quantity,
            groups = selection.groups.map { buildRequestGroup(it) }
        )
    }

    fun resetCheckoutState() {
        _checkoutState.value = CheckoutState.Idle
    }

    fun getTotal(): BigDecimal {
        return (_quoteState.value as? QuoteState.Valid)?.preview?.total ?: BigDecimal.ZERO
    }

    companion object {
        fun factory(
            menuRepository: IMenuRepository,
            manualPosOrderRepository: IManualPosOrderRepository,
            promotionRepository: IPromotionRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PosViewModel(menuRepository, manualPosOrderRepository, promotionRepository) as T
                }
            }
    }
}

package com.restaurant.sushimei.frontend.ui.pos

import com.restaurant.sushimei.frontend.PrintManager
import com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

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
    data class ConfirmedWithPrintWarning(val response: ManualPosOrderResponse, val orderId: Long, val requestId: String, val message: String) : CheckoutState

    data class Error(val message: String) : CheckoutState
    data class OpenSaleSuccess(val response: com.restaurant.sushimei.frontend.data.model.OpenSaleResponse) : CheckoutState
    data class OpenSaleConfirmedWithPrintWarning(val response: com.restaurant.sushimei.frontend.data.model.OpenSaleResponse, val orderId: Long, val requestId: String, val message: String) : CheckoutState
}


sealed interface CurrentPrintUiState {
    object Idle : CurrentPrintUiState
    data class Printing(val jobId: String, val orderId: Long) : CurrentPrintUiState
    data class Failed(val jobId: String, val orderId: Long, val message: String) : CurrentPrintUiState
    object Printed : CurrentPrintUiState
    data class InternalCopyPrinting(val jobId: String, val orderId: Long) : CurrentPrintUiState
    data class InternalCopyFailed(val jobId: String, val orderId: Long, val message: String) : CurrentPrintUiState
    object InternalCopyPrinted : CurrentPrintUiState
}

sealed interface PosUiState {
    object Loading : PosUiState

    data class Success(
        val categories: List<String> = listOf("Todos"),
        val selectedCategory: String? = null,
        val allProducts: List<MenuItem> = emptyList(),
        val filteredProducts: List<MenuItem> = emptyList(),
        val activePromotions: List<Promotion> = emptyList(),
        val promotionLoadError: String? = null,
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
    private val promotionRepository: IPromotionRepository,
    private val printManager: PrintManager,
    private val printJobRepository: IPrintJobRepository
) : ViewModel() {
    // --- Estado interno ---

    private val _currentPrintJobId = MutableStateFlow<String?>(null)

    val currentPrintState: StateFlow<CurrentPrintUiState> = _currentPrintJobId.flatMapLatest { jobId ->
        if (jobId == null) {
            flowOf(CurrentPrintUiState.Idle)
        } else {
            combine(
                printJobRepository.observeJobById(jobId),
                printJobRepository.observeAttemptsForJob(jobId)
            ) { job, attempts ->
                if (job == null) return@combine CurrentPrintUiState.Idle

                if (job.status == PrintJobStatus.PENDING || job.status == PrintJobStatus.PRINTING) {
                    return@combine CurrentPrintUiState.Printing(job.id, job.documentId)
                }
                if (job.status == PrintJobStatus.FAILED || job.status == PrintJobStatus.INTERRUPTED) {
                    return@combine CurrentPrintUiState.Failed(job.id, job.documentId, job.lastError ?: "Error desconocido")
                }

                val internalCopyAttempts = attempts.filter { it.type == com.restaurant.sushimei.frontend.data.model.PrintAttemptType.INTERNAL_COPY }
                if (internalCopyAttempts.isEmpty()) {
                    return@combine CurrentPrintUiState.Printed
                }

                val latestInternalCopy = internalCopyAttempts.first()
                when (latestInternalCopy.status) {
                    com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.PRINTING -> CurrentPrintUiState.InternalCopyPrinting(job.id, job.documentId)
                    com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.FAILED, com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.INTERRUPTED -> CurrentPrintUiState.InternalCopyFailed(job.id, job.documentId, latestInternalCopy.error ?: "Error desconocido")
                    com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus.SUCCEEDED -> CurrentPrintUiState.InternalCopyPrinted
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CurrentPrintUiState.Idle)

    fun printInternalCopy() {
        val jobId = _currentPrintJobId.value ?: return
        printManager.printInternalCopy(jobId)
    }

    fun retryCurrentPrint() {
        val jobId = _currentPrintJobId.value ?: return
        printManager.retryPrintJob(jobId)
    }


    private val _allProducts = MutableStateFlow<List<MenuItem>>(emptyList())

    private val _selectedCategory = MutableStateFlow<String?>(null)

    private val _currentCart = MutableStateFlow<List<ConfiguredProduct>>(emptyList())

    private val _activePromotions = MutableStateFlow<List<Promotion>>(emptyList())

    private val _promotionLoadError = MutableStateFlow<String?>(null)

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
        combine(
            _allProducts,
            _selectedCategory,
            _currentCart,
            _activePromotions,
            _promotionLoadError
        ) { all, category, cart, promotions, promotionError ->
            CatalogState(all, category, cart, promotions, promotionError)
        },
        combine(_isLoading, _quoteState, _checkoutState, ::Triple),
        combine(_fulfillmentType, _paymentMethod, _pickupName, _deliveryAddress, _cashDenomination) { f, p, pn, da, cd ->

            MetadataState(f, p, pn, da, cd)
        }
    ) { catalog, (loading, quote, checkout), metadata ->

        if (loading) {
            PosUiState.Loading
        } else {
            val categories = buildList {
                add("Todos")

                addAll(catalog.allProducts.map { it.categoria }.distinct().sorted())
            }

            val filtered = if (catalog.selectedCategory == null || catalog.selectedCategory == "Todos") {
                catalog.allProducts
            } else {
                catalog.allProducts.filter { it.categoria == catalog.selectedCategory }
            }

            PosUiState.Success(
                categories = categories,
                selectedCategory = catalog.selectedCategory,
                allProducts = catalog.allProducts,
                filteredProducts = filtered,
                activePromotions = catalog.activePromotions,
                promotionLoadError = catalog.promotionLoadError,
                currentCart = catalog.cart,
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

    private data class CatalogState(
        val allProducts: List<MenuItem>,
        val selectedCategory: String?,
        val cart: List<ConfiguredProduct>,
        val activePromotions: List<Promotion>,
        val promotionLoadError: String?
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

        refreshActivePromotions()

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
                } catch (e: ApiException) {
                    if (e.code == "PROMOTION_REWARD_INVALID" && recoverUnavailablePromotionLines()) {
                        return@collect
                    }
                    _quoteState.value = QuoteState.Error(
                        "Error al cotizar orden: ${e.message}${e.referenceSuffix()}"
                    )
                } catch (e: Exception) {
                    _quoteState.value = QuoteState.Error("Error al cotizar orden: ${e.message}")
                }
            }
        }
    }

    fun refreshActivePromotions() {
        viewModelScope.launch {
            _promotionLoadError.value = null
            try {
                _activePromotions.value = promotionRepository.getActivePromotions()
                    .sortedWith(compareByDescending<Promotion> { it.priority }.thenBy { it.id })
            } catch (e: ApiException) {
                _activePromotions.value = emptyList()
                _promotionLoadError.value =
                    "No se pudieron cargar las promociones activas.${e.referenceSuffix()}"
            } catch (_: Exception) {
                _activePromotions.value = emptyList()
                _promotionLoadError.value = "No se pudieron cargar las promociones activas."
            }
        }
    }

    private suspend fun recoverUnavailablePromotionLines(): Boolean {
        return try {
            val applicablePromotions = promotionRepository.getActivePromotions()
                .sortedWith(compareByDescending<Promotion> { it.priority }.thenBy { it.id })
            val applicableIds = applicablePromotions.mapTo(mutableSetOf()) { it.id }
            val current = _currentCart.value
            val recovered = current.filter { product ->
                product.promotionSelection?.promotionId?.let(applicableIds::contains) ?: true
            }

            _activePromotions.value = applicablePromotions
            if (recovered.size == current.size) {
                false
            } else {
                _currentCart.value = recovered
                _promotionLoadError.value = "Una promoción dejó de estar disponible y se retiró de la orden."
                invalidateRequestId()
                true
            }
        } catch (_: Exception) {
            _promotionLoadError.value = "La promoción cambió y no se pudo actualizar. Intenta de nuevo."
            false
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

                val index = currentList.indexOfFirst {
                    it.promotionSelection == null &&
                        it.menuItemId == product.menuItemId &&
                        it.groups.isEmpty() &&
                        it.omittedComponents.isEmpty() &&
                        it.note.isNullOrBlank()
                }

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
            it.promotionSelection == null &&
                configuredProduct.promotionSelection == null &&
                it.menuItemId == configuredProduct.menuItemId &&
                it.groups == configuredProduct.groups &&
                it.omittedComponents == configuredProduct.omittedComponents &&
                it.note == configuredProduct.note
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

    fun eligibleProducts(promotion: Promotion): List<MenuItem> {
        val itemTargets = promotion.targets
            .filter { it.type == PromotionTargetType.ITEM }
            .mapTo(mutableSetOf()) { it.targetId }
        val tagTargets = promotion.targets
            .filter { it.type == PromotionTargetType.TAG }
            .mapTo(mutableSetOf()) { it.targetId }

        return _allProducts.value.filter { item ->
            item.id in itemTargets || item.tags.any { it.active && it.id in tagTargets }
        }
    }

    fun addPromotionBundle(
        promotion: Promotion,
        menuItem: MenuItem,
        purchasedProduct: ConfiguredProduct? = null,
        rewardProducts: List<ConfiguredProduct> = emptyList()
    ) {
        if (eligibleProducts(promotion).none { it.id == menuItem.id }) return

        val purchasedQuantity: Int
        val rewardQuantity: Int
        val isEligibleItemBenefit: Boolean
        when (val benefit = promotion.benefit) {
            is PromotionBenefit.FixedUnitPrice -> {
                purchasedQuantity = 1
                rewardQuantity = 0
                isEligibleItemBenefit = false
            }
            is PromotionBenefit.BuyXGetY -> {
                purchasedQuantity = benefit.buyQuantity
                rewardQuantity = benefit.rewardQuantity
                isEligibleItemBenefit = PromotionBenefit.BuyXGetY.isEligibleItemVariant(benefit.type)
            }
        }

        // The purchased product's menuItemId must always match the chosen menu item.
        if (purchasedProduct != null && purchasedProduct.menuItemId != menuItem.id) return

        // requiresConfiguration is a STANDALONE ordering context.
        // For ELIGIBLE_ITEM BOGO, we never require a configured purchasedProduct — the picker
        // provides pre-quoted slots directly. Only gate for SAME_ITEM standalone configurator flow.
        if (!isEligibleItemBenefit && menuItem.requiresConfiguration && purchasedProduct == null) return

        // Validate reward list when rewards are expected.
        if (rewardQuantity > 0 && rewardProducts.isNotEmpty()) {
            val rewardCountOk = rewardProducts.size == rewardQuantity
            // For SAME_ITEM: every reward must be the same product as the purchased item.
            // For ELIGIBLE_ITEM: reward products may differ — backend is authoritative for eligibility.
            val rewardItemsOk = if (isEligibleItemBenefit) {
                true
            } else {
                rewardProducts.all { it.menuItemId == menuItem.id }
            }
            if (!rewardCountOk || !rewardItemsOk) return
        }
        // For SAME_ITEM configurable: ensure all reward slots are provided before committing.
        if (!isEligibleItemBenefit && menuItem.requiresConfiguration && rewardQuantity > 0 && rewardProducts.size != rewardQuantity) return

        viewModelScope.launch {
            try {
                val purchased = purchasedProduct ?: quoteUnconfiguredProduct(menuItem, purchasedQuantity)
                val rewards = when {
                    rewardQuantity == 0 -> emptyList()
                    rewardProducts.isNotEmpty() -> rewardProducts.mapIndexed { index, reward ->
                        ConfiguredRewardConfiguration(
                            rewardOrdinal = index + 1,
                            menuItemId = reward.menuItemId,
                            groups = reward.groups
                        )
                    }
                    else -> (1..rewardQuantity).map { ordinal ->
                        ConfiguredRewardConfiguration(rewardOrdinal = ordinal)
                    }
                }

                val line = purchased.copy(
                    id = UUID.randomUUID().toString(),
                    quantity = purchasedQuantity,
                    total = purchased.unitTotal * BigDecimal(purchasedQuantity),
                    promotionSelection = PromotionLineSelection(
                        promotionId = promotion.id,
                        promotionName = promotion.name,
                        rewardConfigurations = rewards
                    )
                )

                _currentCart.value = _currentCart.value + line
                invalidateRequestId()
            } catch (_: Exception) {
                _quoteState.value = QuoteState.Error("No se pudo configurar la promoción seleccionada.")
            }
        }
    }

    /**
     * Dedicated BOGO entry point for BUY_X_GET_Y_ELIGIBLE_ITEM promotions.
     *
     * Called by FlexibleBogoPickerDialog after the cashier selects slots from a single screen.
     * requiresConfiguration is intentionally ignored here — roll configuration is a standalone
     * ordering context, never a BOGO selection context.
     *
     * @param promotion The active promotion being applied.
     * @param purchasedMenuItem The MenuItem occupying the purchased slot (slot 1).
     * @param rewardMenuItems The MenuItems occupying the reward slots (slots 2..N), in selection order.
     */
    fun addEligibleItemBundle(
        promotion: Promotion,
        purchasedMenuItem: MenuItem,
        rewardMenuItems: List<MenuItem>
    ) {
        if (eligibleProducts(promotion).none { it.id == purchasedMenuItem.id }) return
        val bogo = promotion.benefit as? PromotionBenefit.BuyXGetY ?: return
        if (!PromotionBenefit.BuyXGetY.isEligibleItemVariant(bogo.type)) return
        if (rewardMenuItems.size != bogo.rewardQuantity) return

        viewModelScope.launch {
            try {
                // Quote the purchased item at the backend. No configuration groups — BOGO context.
                val purchased = quoteUnconfiguredProduct(purchasedMenuItem, bogo.buyQuantity)

                // Build reward configuration slots from raw MenuItem ids.
                // No configuration groups — reward configuration is not part of BOGO selection.
                val rewards = rewardMenuItems.mapIndexed { index, rewardItem ->
                    ConfiguredRewardConfiguration(
                        rewardOrdinal = index + 1,
                        menuItemId = rewardItem.id,
                        groups = emptyList()
                    )
                }

                val line = purchased.copy(
                    id = UUID.randomUUID().toString(),
                    quantity = bogo.buyQuantity,
                    total = purchased.unitTotal * BigDecimal(bogo.buyQuantity),
                    promotionSelection = PromotionLineSelection(
                        promotionId = promotion.id,
                        promotionName = promotion.name,
                        rewardConfigurations = rewards
                    )
                )

                _currentCart.value = _currentCart.value + line
                invalidateRequestId()
            } catch (_: Exception) {
                _quoteState.value = QuoteState.Error("No se pudo agregar la promoción al carrito.")
            }
        }
    }

    private suspend fun quoteUnconfiguredProduct(menuItem: MenuItem, quantity: Int): ConfiguredProduct {
        val quote = menuRepository.quoteItem(
            menuItem.id,
            ItemQuoteRequestDto(quantity = quantity, groups = emptyList())
        )
        return ConfiguredProduct(
            menuItemId = quote.menuItemId,
            name = quote.name,
            quantity = quote.quantity,
            baseUnitPrice = quote.baseUnitPrice,
            unitTotal = quote.unitTotal,
            total = quote.total,
            groups = emptyList()
        )
    }

    fun incrementCartItem(product: ConfiguredProduct, onRequiresConfiguration: () -> Unit) {
        if (product.promotionSelection != null) return

        val menuItem = _allProducts.value.find { it.id == product.menuItemId } ?: return

        if (menuItem.requiresConfiguration) {
            onRequiresConfiguration()
        } else {
            addToCart(menuItem)
        }
    }

    fun removeFromCart(configuredProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()

        val index = currentList.indexOfFirst { it.id == configuredProduct.id }

        if (index >= 0) {
            val existing = currentList[index]

            if (existing.promotionSelection != null) {
                currentList.removeAt(index)
            } else if (existing.quantity > 1) {
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
        denomination: BigDecimal?,
        total: BigDecimal
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

            if (payment == PaymentMethod.CASH) {
                if (denomination == null || denomination < total) {
                    return "Debes ingresar una denominación válida mayor a cero."
                }
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

        val total = getTotal()
        val validationError = validateCheckout(fulfillment, payment, pickup, address, denomination, total)

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
            cashDenomination = if (fulfillment == FulfillmentType.DELIVERY && payment == PaymentMethod.CASH) denomination else null,
            lines = items.map { buildRequestLine(it) }
        )

        viewModelScope.launch {
            try {
                val response = manualPosOrderRepository.submitOrder(requestSnapshot)

                if (response.result == OrderResult.CREATED || response.result == OrderResult.ALREADY_CREATED) {
                    try {
                        val job = printManager.enqueuePrintJob(com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER, response.id, response.requestId)
                        _currentPrintJobId.value = job.id
                        clearCart()
                        invalidateRequestId()
                        _checkoutState.value = CheckoutState.Success(response)
                    } catch (e: Exception) {
                        clearCart()
                        invalidateRequestId()
                        _checkoutState.value = CheckoutState.ConfirmedWithPrintWarning(
                            response = response,
                            orderId = response.id,
                            requestId = response.requestId,
                            message = "La orden se confirmó exitosamente, pero no se pudo encolar la impresión local: ${e.message}"
                        )
                    }
                } else {

                    _checkoutState.value = CheckoutState.Error("Respuesta inesperada del servidor.")
                }
            } catch (e: ApiException) {
                if (e.code == "ORDER_PROMOTION_CONFLICT" || e.code == "PROMOTION_REWARD_INVALID") {
                    recoverUnavailablePromotionLines()
                }
                _checkoutState.value = CheckoutState.Error(mapApiError(e))
            } catch (e: java.io.IOException) {
                _checkoutState.value = CheckoutState.Error("Error de red: La orden no pudo confirmarse. Intenta de nuevo.")

                // No invalidamos el requestId para permitir reintento seguro
            } catch (e: Exception) {
                _checkoutState.value = CheckoutState.Error("Error inesperado al procesar la orden. Intenta de nuevo.")

                // No invalidamos el requestId para permitir reintento seguro
            }
        }
    }

    private fun mapApiError(e: ApiException): String {
        val message = when (e.code) {
            "ORDER_INVALID" -> "Datos de orden inválidos. Revisa la información."

            "ORDER_CASH_DENOMINATION_INSUFFICIENT" -> "La denominación es menor al total del pedido."

            "ORDER_IDEMPOTENCY_CONFLICT" -> "Conflicto de idempotencia. Esta orden puede haber sido procesada parcialmente."

            "ORDER_MENU_ITEM_NOT_FOUND" -> "Un producto ya no existe en el catálogo."

            "ORDER_MENU_ITEM_UNAVAILABLE" -> "Un producto seleccionado no está disponible."

            "ORDER_CONFIGURATION_INVALID" -> "Configuración de producto inválida."

            "ORDER_PROMOTION_CONFLICT", "PROMOTION_REWARD_INVALID" ->
                "La promoción cambió o dejó de estar disponible. Revisa la orden e intenta de nuevo."

            "ORDER_FORBIDDEN_OPERATION", "AUTH_FORBIDDEN" -> "No tienes permisos para realizar esta operación."
            "BUSINESS_DAY_CLOSED" -> "El día operativo ya está cerrado. No se pueden procesar más órdenes."
            "BUSINESS_DAY_HAS_ACTIVE_ORDERS" -> "No se puede cerrar la caja mientras existan órdenes activas."

            else -> "Error del servidor. La orden no pudo confirmarse. Intenta de nuevo."
        }
        return message + e.referenceSuffix()
    }

    private fun buildRequestLine(product: ConfiguredProduct): PosOrderRequestLineDto {
        return PosOrderRequestLineDto(
            lineKey = product.id,
            menuItemId = product.menuItemId,
            quantity = product.quantity,
            groups = product.groups.map { buildRequestGroup(it) },
            rewardConfigurations = product.promotionSelection?.rewardConfigurations?.map { reward ->
                QuoteRequestRewardConfigDto(
                    rewardOrdinal = reward.rewardOrdinal,
                    menuItemId = reward.menuItemId,
                    groups = reward.groups.map { buildRequestGroup(it) }
                )
            } ?: emptyList(),
            omittedComponentIds = product.omittedComponents.map { it.id },
            note = product.note
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


    fun retryPrintRegistration(orderId: Long, requestId: String, response: ManualPosOrderResponse) {
        viewModelScope.launch {
            try {
                val job = printManager.enqueuePrintJob(com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER, orderId, requestId)
                _currentPrintJobId.value = job.id
                _checkoutState.value = CheckoutState.Success(response)
            } catch (e: Exception) {
                _checkoutState.value = CheckoutState.ConfirmedWithPrintWarning(
                    response = response,
                    orderId = orderId,
                    requestId = requestId,
                    message = "La orden se confirmó exitosamente, pero aún no se puede registrar la impresión: ${e.message}"
                )
            }
        }
    }

    fun resetCheckoutState() {
        _checkoutState.value = CheckoutState.Idle
        _currentPrintJobId.value = null
    }

    fun getTotal(): BigDecimal {
        return (_quoteState.value as? QuoteState.Valid)?.preview?.total ?: BigDecimal.ZERO
    }

    companion object {
        fun factory(
            menuRepository: IMenuRepository,
            manualPosOrderRepository: IManualPosOrderRepository,
            promotionRepository: IPromotionRepository,
            printManager: PrintManager,
            printJobRepository: IPrintJobRepository
        ): ViewModelProvider.Factory =

            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")

                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PosViewModel(menuRepository, manualPosOrderRepository, promotionRepository, printManager, printJobRepository) as T
                }
            }
    }

    fun submitOpenSale(
        description: String,
        amount: java.math.BigDecimal,
        paymentMethod: com.restaurant.sushimei.frontend.data.model.PaymentMethod,
        cashDenomination: java.math.BigDecimal?
    ) {
        if (_checkoutState.value == CheckoutState.Loading) return
        _checkoutState.value = CheckoutState.Loading

        viewModelScope.launch {
            try {
                val finalDenom = if (paymentMethod == com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH) cashDenomination else null
                val request = com.restaurant.sushimei.frontend.data.model.OpenSaleRequest(
                    requestId = java.util.UUID.randomUUID().toString(),
                    description = description,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    cashDenomination = finalDenom
                )
                val response = manualPosOrderRepository.createOpenSale(request)
                if (response.result == "CREATED" || response.result == "ALREADY_CREATED") {
                    try {
                        printManager.enqueuePrintJob(
                            com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER,
                            response.id,
                            request.requestId
                        )
                        _checkoutState.value = CheckoutState.OpenSaleSuccess(response)
                    } catch (e: Exception) {
                        _checkoutState.value = CheckoutState.OpenSaleConfirmedWithPrintWarning(
                            response, response.id, request.requestId, "Venta registrada, pero error al imprimir: "
                        )
                    }
                } else {
                    _checkoutState.value = CheckoutState.Error("Fallo inesperado del servidor: ")
                }
            } catch (e: ApiException) {
                _checkoutState.value = CheckoutState.Error(mapApiError(e))
            } catch (e: Exception) {
                _checkoutState.value = CheckoutState.Error("Error inesperado al procesar la orden. Intenta de nuevo.")
            }
        }
    }

}

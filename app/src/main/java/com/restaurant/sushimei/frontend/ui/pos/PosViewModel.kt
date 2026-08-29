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
import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
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


// ============================================================================
// Active POS Order Management State (feat/pos-order-cancellation-ui)
// ============================================================================

// The statuses the backend allows voiding (only physical POS sources matter here,
// but the backend remains authoritative; Android filtering is presentation safety only).
internal val POS_VOIDABLE_SOURCES = setOf("ANDROID_MANUAL", "COUNTER")
internal val POS_ACTIVE_STATUSES = setOf("PENDING_VALIDATION", "PENDING", "PREPARING", "READY")

sealed interface ActiveOrderManagementState {
    /** Surface is closed. */
    object Idle : ActiveOrderManagementState

    /** Loading the first page / after refresh. */
    object LoadingOrders : ActiveOrderManagementState

    /** Orders loaded and displayed. confirmingVoid contains the order the user wants to cancel. */
    data class Content(
        val orders: List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto>,
        val confirmingVoid: Long? = null,   // orderId of the order being confirmed
        val submittingVoidFor: Long? = null, // orderId currently in-flight
        val voidError: String? = null,       // transient error from last void attempt
        val voidSuccessMessage: String? = null
    ) : ActiveOrderManagementState

    data class Error(val message: String) : ActiveOrderManagementState
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
        val manualCart: List<com.restaurant.sushimei.frontend.data.model.ManualCartLine> = emptyList(),
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
    private val printJobRepository: IPrintJobRepository,
    private val operationalOrderRepository: IOperationalOrderRepository? = null
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
    private val _manualCart = MutableStateFlow<List<com.restaurant.sushimei.frontend.data.model.ManualCartLine>>(emptyList())

    private val _activePromotions = MutableStateFlow<List<Promotion>>(emptyList())

    private val _promotionLoadError = MutableStateFlow<String?>(null)

    private val _isLoading = MutableStateFlow(true)

    private val _quoteState = MutableStateFlow<QuoteState>(QuoteState.Idle)





    private val _checkoutState = MutableStateFlow<CheckoutState>(CheckoutState.Idle)

    // --- Active Order Management ---
    private val _activeOrderManagementState = MutableStateFlow<ActiveOrderManagementState>(ActiveOrderManagementState.Idle)
    private var activeOrderSessionGeneration: Long = 0L
    val activeOrderManagementState: StateFlow<ActiveOrderManagementState> = _activeOrderManagementState.asStateFlow()

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
            _promotionLoadError,
            _manualCart
        ) { args: Array<Any?> ->
            CatalogState(
                allProducts = args[0] as List<com.restaurant.sushimei.frontend.data.model.MenuItem>,
                selectedCategory = args[1] as String?,
                cart = args[2] as List<com.restaurant.sushimei.frontend.data.model.ConfiguredProduct>,
                activePromotions = args[3] as List<com.restaurant.sushimei.frontend.data.model.Promotion>,
                promotionLoadError = args[4] as String?,
                manualCart = args[5] as List<com.restaurant.sushimei.frontend.data.model.ManualCartLine>
            )
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
                manualCart = catalog.manualCart,
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
        val manualCart: List<com.restaurant.sushimei.frontend.data.model.ManualCartLine>,
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

    fun replaceConfiguredProduct(oldId: String, newProduct: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == oldId }
        if (index != -1) {
            val oldProduct = currentList[index]
            val preservedProduct = newProduct.copy(
                id = oldProduct.id,
                promotionSelection = oldProduct.promotionSelection
            )
            currentList[index] = preservedProduct
            _currentCart.value = currentList
            invalidateRequestId()
        }
    }

    fun replaceConfiguredReward(rootProductId: String, rewardOrdinal: Int, configuredRewardData: ConfiguredProduct) {
        val currentList = _currentCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == rootProductId }
        if (index != -1) {
            val oldProduct = currentList[index]
            val oldPromo = oldProduct.promotionSelection ?: return

            val updatedRewards = oldPromo.rewardConfigurations.map { rew ->
                if (rew.rewardOrdinal == rewardOrdinal) {
                    rew.copy(
                        groups = configuredRewardData.groups,
                        omittedComponents = configuredRewardData.omittedComponents,
                        note = configuredRewardData.note
                    )
                } else {
                    rew
                }
            }

            val updatedPromo = oldPromo.copy(rewardConfigurations = updatedRewards)
            val preservedProduct = oldProduct.copy(promotionSelection = updatedPromo)

            currentList[index] = preservedProduct
            _currentCart.value = currentList
            invalidateRequestId()

            // Re-quote the entire cart since reward configuration changed (which might affect its own catalog pricing validity though rewards are usually free, they can have paid additions)

        }
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
        if (_currentCart.value.isNotEmpty() || _manualCart.value.isNotEmpty()) {
            _currentCart.value = emptyList()
            _manualCart.value = emptyList()
            pendingRequestId = null
        }
    }

    private fun resetCheckoutMetadata() {
        _fulfillmentType.value = FulfillmentType.PICKUP
        _paymentMethod.value = PaymentMethod.CASH
        _pickupName.value = ""
        _deliveryAddress.value = ""
        _cashDenomination.value = null

        invalidateRequestId()
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

        if ((items.isEmpty() && _manualCart.value.isEmpty()) || _checkoutState.value == CheckoutState.Loading) return

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

        val manualItems = _manualCart.value
        val requestSnapshot = ManualPosOrderRequest(
            requestId = pendingRequestId.toString(),
            fulfillmentType = fulfillment,
            paymentMethod = payment,
            deliveryAddress = if (fulfillment == FulfillmentType.DELIVERY) address else null,
            pickupName = if (fulfillment == FulfillmentType.PICKUP) pickup else null,
            cashDenomination = if (fulfillment == FulfillmentType.DELIVERY && payment == PaymentMethod.CASH) denomination else null,
            lines = items.map { buildRequestLine(it) },
            manualLines = manualItems.map {
                com.restaurant.sushimei.frontend.data.model.ManualPricedLineRequest(
                    lineKey = it.lineKey,
                    description = it.description,
                    quantity = it.quantity,
                    unitAmount = it.unitAmount
                )
            }
        )

        viewModelScope.launch {
            try {
                val response = manualPosOrderRepository.submitOrder(requestSnapshot)

                if (response.result == OrderResult.CREATED || response.result == OrderResult.ALREADY_CREATED) {
                    try {
                        val job = printManager.enqueuePrintJob(com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER, response.id, response.requestId)
                        _currentPrintJobId.value = job.id
                        clearCart()
                        resetCheckoutMetadata()
                        _checkoutState.value = CheckoutState.Success(response)
                    } catch (e: Exception) {
                        clearCart()
                        resetCheckoutMetadata()
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
                    groups = reward.groups.map { buildRequestGroup(it) },
                    omittedComponentIds = reward.omittedComponents.map { it.id },
                    note = reward.note
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
            groups = selection.groups.map { buildRequestGroup(it) },
            omittedComponentIds = selection.omittedComponents.map { it.id },
            note = selection.note
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

    fun retryOpenSalePrintRegistration(orderId: Long, requestId: String, response: com.restaurant.sushimei.frontend.data.model.OpenSaleResponse) {
        viewModelScope.launch {
            try {
                val job = printManager.enqueuePrintJob(com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER, orderId, requestId)
                _currentPrintJobId.value = job.id
                _checkoutState.value = CheckoutState.OpenSaleSuccess(response)
            } catch (e: Exception) {
                _checkoutState.value = CheckoutState.OpenSaleConfirmedWithPrintWarning(
                    response = response,
                    orderId = orderId,
                    requestId = requestId,
                    message = "La orden se confirmó exitosamente, pero aún no se puede registrar la impresión: ${e.message}"
                )
            }
        }
    }

    fun addManualLine(description: String, unitAmount: java.math.BigDecimal) {
        val newLine = com.restaurant.sushimei.frontend.data.model.ManualCartLine(
            description = description,
            unitAmount = unitAmount
        )
        _manualCart.value = _manualCart.value + newLine
        pendingRequestId = null
    }

    fun removeManualLine(lineKey: String) {
        _manualCart.value = _manualCart.value.filter { it.lineKey != lineKey }
        pendingRequestId = null
    }

    fun resetCheckoutState() {
        _checkoutState.value = CheckoutState.Idle
        _currentPrintJobId.value = null
    }

    fun getTotal(): BigDecimal {
        val quoteTotal = (_quoteState.value as? QuoteState.Valid)?.preview?.total ?: java.math.BigDecimal.ZERO
        val manualTotal = _manualCart.value.sumOf { it.total }
        return quoteTotal + manualTotal
    }

    companion object {
        fun factory(
            menuRepository: IMenuRepository,
            manualPosOrderRepository: IManualPosOrderRepository,
            promotionRepository: IPromotionRepository,
            printManager: PrintManager,
            printJobRepository: IPrintJobRepository,
            operationalOrderRepository: IOperationalOrderRepository? = null
        ): ViewModelProvider.Factory =

            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")

                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PosViewModel(menuRepository, manualPosOrderRepository, promotionRepository, printManager, printJobRepository, operationalOrderRepository) as T
                }
            }
    }


    private var pendingOpenSaleRequestId: java.util.UUID? = null
    private var pendingOpenSaleFingerprint: String? = null

    fun submitOpenSale(
        description: String,
        amount: java.math.BigDecimal,
        paymentMethod: com.restaurant.sushimei.frontend.data.model.PaymentMethod,
        cashDenomination: java.math.BigDecimal?
    ) {
        if (_checkoutState.value == CheckoutState.Loading) return
        _checkoutState.value = CheckoutState.Loading

        val normalizedDesc = description.trim().lowercase()
        val finalDenom = if (paymentMethod == com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH) cashDenomination else null
        val fingerprint = "$normalizedDesc|${amount.toPlainString()}|${paymentMethod.name}|${finalDenom?.toPlainString() ?: "null"}"

        if (pendingOpenSaleRequestId == null || pendingOpenSaleFingerprint != fingerprint) {
            pendingOpenSaleRequestId = java.util.UUID.randomUUID()
            pendingOpenSaleFingerprint = fingerprint
        }

        val requestId = pendingOpenSaleRequestId!!.toString()

        viewModelScope.launch {
            try {
                val request = com.restaurant.sushimei.frontend.data.model.OpenSaleRequest(
                    requestId = requestId,
                    description = description,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    cashDenomination = finalDenom
                )
                val response = manualPosOrderRepository.createOpenSale(request)
                if (response.result == "CREATED" || response.result == "ALREADY_CREATED") {
                    pendingOpenSaleRequestId = null
                    pendingOpenSaleFingerprint = null
                    try {
                        val job = printManager.enqueuePrintJob(
                            com.restaurant.sushimei.frontend.data.model.PrintDocumentType.ORDER,
                            response.id,
                            request.requestId
                        )
                        _currentPrintJobId.value = job.id
                        _checkoutState.value = CheckoutState.OpenSaleSuccess(response)
                    } catch (e: Exception) {
                        _checkoutState.value = CheckoutState.OpenSaleConfirmedWithPrintWarning(
                            response, response.id, request.requestId, "Venta registrada, pero error al imprimir: ${e.message}"
                        )
                    }
                } else {
                    _checkoutState.value = CheckoutState.Error("Fallo inesperado del servidor: ${response.result}")
                }
            } catch (e: ApiException) {
                _checkoutState.value = CheckoutState.Error(mapApiError(e))
            } catch (e: Exception) {
                _checkoutState.value = CheckoutState.Error("Error inesperado al procesar la orden. Intenta de nuevo.")
            }
        }
    }
    // ============================================================================
    // Active POS Order Management (feat/pos-order-cancellation-ui)
    // ============================================================================

    /** Open the active-order management surface and lazily load orders from backend. */
    fun openActiveOrderManagement() {
        val repo = operationalOrderRepository ?: return
        val currentSession = ++activeOrderSessionGeneration
        _activeOrderManagementState.value = ActiveOrderManagementState.LoadingOrders
        viewModelScope.launch {
            try {
                val raw = repo.getOperationalActiveOrders()
                val filtered = raw.filter { order ->
                    order.orderSource in POS_VOIDABLE_SOURCES && order.status in POS_ACTIVE_STATUSES
                }
                if (currentSession != activeOrderSessionGeneration) return@launch
                _activeOrderManagementState.value = ActiveOrderManagementState.Content(orders = filtered)
            } catch (e: Exception) {
                if (currentSession != activeOrderSessionGeneration) return@launch
                _activeOrderManagementState.value = ActiveOrderManagementState.Error(
                    message = "No se pudieron cargar los pedidos activos: ${e.message ?: "Error desconocido"}"
                )
            }
        }
    }

    /** Refresh the active-order list while remaining in Content state. */
    fun refreshActiveOrders() {
        val repo = operationalOrderRepository ?: return
        if (_activeOrderManagementState.value !is ActiveOrderManagementState.Content &&
            _activeOrderManagementState.value !is ActiveOrderManagementState.Error
        ) return
        val currentSession = activeOrderSessionGeneration
        // Maintain the existing Content if present, just reload data
        viewModelScope.launch {
            try {
                val raw = repo.getOperationalActiveOrders()
                val filtered = raw.filter { order ->
                    order.orderSource in POS_VOIDABLE_SOURCES && order.status in POS_ACTIVE_STATUSES
                }
                if (currentSession != activeOrderSessionGeneration) return@launch
                val existing = _activeOrderManagementState.value
                if (existing is ActiveOrderManagementState.Content) {
                    _activeOrderManagementState.value = existing.copy(
                        orders = filtered,
                        voidError = null
                    )
                } else {
                    _activeOrderManagementState.value = ActiveOrderManagementState.Content(orders = filtered)
                }
            } catch (e: Exception) {
                if (currentSession != activeOrderSessionGeneration) return@launch
                val existing = _activeOrderManagementState.value
                if (existing is ActiveOrderManagementState.Content) {
                    _activeOrderManagementState.value = existing.copy(
                        voidError = "Error al actualizar pedidos: ${e.message ?: "Error desconocido"}"
                    )
                } else {
                    _activeOrderManagementState.value = ActiveOrderManagementState.Error(
                        message = "Error al actualizar pedidos: ${e.message ?: "Error desconocido"}"
                    )
                }
            }
        }
    }

    /** Close the active-order management surface without touching cart or checkout. */
    fun closeActiveOrderManagement() {
        activeOrderSessionGeneration++
        _activeOrderManagementState.value = ActiveOrderManagementState.Idle
    }

    /** Ask for confirmation before voiding. Does NOT call the backend. */
    fun requestVoidConfirmation(orderId: Long) {
        val current = _activeOrderManagementState.value as? ActiveOrderManagementState.Content ?: return
        // Don't allow requesting confirmation while another void is in-flight
        if (current.submittingVoidFor != null) return
        _activeOrderManagementState.value = current.copy(
            confirmingVoid = orderId,
            voidError = null,
            voidSuccessMessage = null
        )
    }

    /** Cancel the pending confirmation dialog without any network call. */
    fun cancelVoidConfirmation() {
        val current = _activeOrderManagementState.value as? ActiveOrderManagementState.Content ?: return
        _activeOrderManagementState.value = current.copy(confirmingVoid = null, voidError = null)
    }

    /**
     * Submit the void to the backend.
     *
     * Android validation:
     * - reason must not be blank after trimming
     * - reason must not exceed 500 characters
     *
     * Does NOT call repository if validation fails.
     * Prevents duplicate submission by checking submittingVoidFor.
     */
    fun submitVoidOrder(orderId: Long, rawReason: String) {
        val current = _activeOrderManagementState.value as? ActiveOrderManagementState.Content ?: return
        val repo = operationalOrderRepository ?: return
        val currentSession = activeOrderSessionGeneration

        // Duplicate-tap guard
        if (current.submittingVoidFor == orderId) return

        val reason = rawReason.trim()
        if (reason.isBlank()) {
            _activeOrderManagementState.value = current.copy(
                voidError = "El motivo de cancelación no puede estar vacío."
            )
            return
        }
        if (reason.length > 500) {
            _activeOrderManagementState.value = current.copy(
                voidError = "El motivo no puede exceder 500 caracteres (actual: ${reason.length})."
            )
            return
        }

        _activeOrderManagementState.value = current.copy(
            submittingVoidFor = orderId,
            voidError = null,
            voidSuccessMessage = null
        )

        val oldSnapshot = current.orders
        viewModelScope.launch {
            try {
                val voidResponse = repo.voidOrder(orderId, reason)
                // On success: refresh list and close confirmation dialog
                try {
                    val raw = repo.getOperationalActiveOrders()
                    val filtered = raw.filter { order ->
                        order.orderSource in POS_VOIDABLE_SOURCES && order.status in POS_ACTIVE_STATUSES
                    }
                    if (currentSession != activeOrderSessionGeneration) return@launch
                    _activeOrderManagementState.value = ActiveOrderManagementState.Content(
                        orders = filtered,
                        confirmingVoid = null,
                        submittingVoidFor = null,
                        voidError = null,
                        voidSuccessMessage = "Pedido #${voidResponse.orderId} cancelado exitosamente."
                    )
                } catch (refreshEx: Exception) {
                    if (currentSession != activeOrderSessionGeneration) return@launch
                    // Void succeeded but refresh failed — authoritatively update last known orders based on response
                    val updatedOrders = if (voidResponse.currentStatus == "VOIDED") {
                        oldSnapshot.filter { it.id != voidResponse.orderId }
                    } else {
                        oldSnapshot
                    }
                    _activeOrderManagementState.value = ActiveOrderManagementState.Content(
                        orders = updatedOrders,
                        confirmingVoid = null,
                        submittingVoidFor = null,
                        voidError = null,
                        voidSuccessMessage = "Pedido #${voidResponse.orderId} cancelado. No se pudo actualizar la lista."
                    )
                }
            } catch (e: com.restaurant.sushimei.frontend.data.api.ApiException) {
                // Backend rejected — refresh to reconcile state
                val errorMsg = when (e.code) {
                    "ORDER_INVALID_TRANSITION" ->
                        "La orden ya no puede cancelarse (estado cambiado).${e.referenceSuffix()}"
                    "ORDER_OPERATION_NOT_SUPPORTED" ->
                        "Esta orden o su origen no admiten cancelación en POS.${e.referenceSuffix()}"
                    "ORDER_INVALID_VOID_REQUEST" ->
                        "Solicitud de cancelación inválida o motivo rechazado.${e.referenceSuffix()}"
                    "ORDER_NOT_FOUND" ->
                        "La orden no fue encontrada.${e.referenceSuffix()}"
                    "ORDER_PAYMENT_NOT_VALIDATABLE" ->
                        "El pago de la orden no está en un estado válido para esta operación.${e.referenceSuffix()}"
                    else ->
                        "Error del servidor al cancelar: ${e.message ?: "Error desconocido"}${e.referenceSuffix()}"
                }
                refreshAfterVoidFailure(currentSession, oldSnapshot, errorMsg)
            } catch (e: java.io.IOException) {
                refreshAfterVoidFailure(currentSession, oldSnapshot, "Error de red. Intenta de nuevo.")
            } catch (e: Exception) {
                refreshAfterVoidFailure(currentSession, oldSnapshot, "Error inesperado: ${e.message ?: "Error desconocido"}")
            }
        }
    }

    private suspend fun refreshAfterVoidFailure(sessionAtStart: Long, fallbackOrders: List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto>, errorMessage: String) {
        val repo = operationalOrderRepository ?: return
        try {
            val raw = repo.getOperationalActiveOrders()
            val filtered = raw.filter { order ->
                order.orderSource in POS_VOIDABLE_SOURCES && order.status in POS_ACTIVE_STATUSES
            }
            if (sessionAtStart != activeOrderSessionGeneration) return
            _activeOrderManagementState.value = ActiveOrderManagementState.Content(
                orders = filtered,
                confirmingVoid = null,
                submittingVoidFor = null,
                voidError = errorMessage
            )
        } catch (_: Exception) {
            if (sessionAtStart != activeOrderSessionGeneration) return
            _activeOrderManagementState.value = ActiveOrderManagementState.Content(
                orders = fallbackOrders,
                confirmingVoid = null,
                submittingVoidFor = null,
                voidError = errorMessage
            )
        }
    }


}

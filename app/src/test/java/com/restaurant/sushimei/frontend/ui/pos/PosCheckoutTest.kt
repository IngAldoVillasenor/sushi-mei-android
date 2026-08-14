package com.restaurant.sushimei.frontend.ui.pos

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.model.*
import com.restaurant.sushimei.frontend.data.repository.IManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PosCheckoutTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: PosViewModel
    private lateinit var fakeManualRepo: FakeManualPosOrderRepository
    private lateinit var fakeMenuRepo: FakeMenuRepository
    private lateinit var fakePromoRepo: FakePromotionRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeManualRepo = FakeManualPosOrderRepository()
        fakeMenuRepo = FakeMenuRepository()
        fakePromoRepo = FakePromotionRepository()

        viewModel = PosViewModel(fakeMenuRepo, fakeManualRepo, fakePromoRepo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun fillCart() {
        val item = ConfiguredProduct(
            id = "test-uuid-1",
            menuItemId = 99L,
            name = "Test Item",
            quantity = 2,
            baseUnitPrice = BigDecimal("50.00"),
            unitTotal = BigDecimal("50.00"),
            total = BigDecimal("100.00"),
            groups = listOf(
                ConfiguredGroup(
                    groupId = 10L,
                    name = "Extras",
                    selections = listOf(
                        ConfiguredSelection(
                            menuItemId = 101L,
                            name = "Salsa",
                            quantity = 1,
                            catalogUnitPrice = BigDecimal("10.00"),
                            priceAdjustment = BigDecimal("5.00"),
                            groups = emptyList()
                        )
                    )
                )
            )
        )
        viewModel.addConfiguredProduct(item)
    }

    @Test
    fun `test config mapping strips prices and formats request correctly via Gson serialization`() = runTest {
        fillCart()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateFulfillmentType(FulfillmentType.DELIVERY)
        viewModel.updateDeliveryAddress("123 Fake St")
        viewModel.updatePaymentMethod(PaymentMethod.CASH)
        viewModel.updateCashDenomination(BigDecimal("200.00"))

        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val request = fakeManualRepo.lastRequest
        assertNotNull(request)

        val jsonString = Gson().toJson(request)
        val jsonObj = Gson().fromJson(jsonString, JsonObject::class.java)

        // Ensure authoritative fields are NOT present
        assertFalse(jsonObj.has("total"))
        assertFalse(jsonObj.has("subtotal"))
        assertFalse(jsonObj.has("promotionAdjustment"))
        assertFalse(jsonObj.has("promotionId"))

        val firstLine = jsonObj.getAsJsonArray("lines").get(0).asJsonObject
        assertFalse(firstLine.has("baseUnitPrice"))
        assertFalse(firstLine.has("catalogBaseUnitPrice"))
        assertFalse(firstLine.has("chargedBaseUnitPrice"))
        assertFalse(firstLine.has("unitTotal"))
        assertFalse(firstLine.has("lineTotal"))
        assertFalse(firstLine.has("total"))

        // Ensure configuration is present
        assertEquals("test-uuid-1", firstLine.get("lineKey").asString)
        assertEquals(99L, firstLine.get("menuItemId").asLong)

        val firstGroup = firstLine.getAsJsonArray("groups").get(0).asJsonObject
        val firstSelection = firstGroup.getAsJsonArray("selections").get(0).asJsonObject
        assertEquals(101L, firstSelection.get("menuItemId").asLong)

        assertEquals("123 Fake St", jsonObj.get("deliveryAddress").asString)

        // Reward configurations may be empty but should not have items
        assertTrue(firstLine.has("rewardConfigurations"))
        assertEquals(0, firstLine.getAsJsonArray("rewardConfigurations").size())
    }

    @Test
    fun `purchased and reward configurations remain independent in checkout request`() = runTest {
        val purchasedGroups = listOf(
            ConfiguredGroup(
                groupId = 10L,
                name = "Rollo comprado",
                selections = listOf(
                    ConfiguredSelection(
                        menuItemId = 201L,
                        name = "Comprado empanizado",
                        quantity = 1,
                        catalogUnitPrice = BigDecimal.ZERO,
                        priceAdjustment = BigDecimal.ZERO
                    )
                )
            )
        )
        val rewardGroups = listOf(
            ConfiguredGroup(
                groupId = 20L,
                name = "Rollo gratis",
                selections = listOf(
                    ConfiguredSelection(
                        menuItemId = 202L,
                        name = "Gratis frío",
                        quantity = 1,
                        catalogUnitPrice = BigDecimal.ZERO,
                        priceAdjustment = BigDecimal.ZERO
                    )
                )
            )
        )
        viewModel.addConfiguredProduct(
            ConfiguredProduct(
                id = "promo-line-1",
                menuItemId = 24L,
                name = "California roll",
                quantity = 1,
                baseUnitPrice = BigDecimal("79.00"),
                groups = purchasedGroups,
                unitTotal = BigDecimal("79.00"),
                total = BigDecimal("79.00"),
                promotionSelection = PromotionLineSelection(
                    promotionId = 8L,
                    promotionName = "Jueves 2x1",
                    rewardConfigurations = listOf(
                        ConfiguredRewardConfiguration(
                            rewardOrdinal = 1,
                            groups = rewardGroups
                        )
                    )
                )
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val line = fakeManualRepo.lastRequest?.lines?.single()
        assertNotNull(line)
        assertEquals(10L, line?.groups?.single()?.groupId)
        assertEquals(201L, line?.groups?.single()?.selections?.single()?.menuItemId)
        assertEquals(1, line?.rewardConfigurations?.single()?.rewardOrdinal)
        assertEquals(20L, line?.rewardConfigurations?.single()?.groups?.single()?.groupId)
        assertEquals(
            202L,
            line?.rewardConfigurations?.single()?.groups?.single()?.selections?.single()?.menuItemId
        )

        val serializedLine = Gson().toJsonTree(line).asJsonObject
        assertFalse(serializedLine.has("promotionId"))
        assertFalse(serializedLine.has("promotionName"))
    }

    @Test
    fun `test idempotency requestId lifecycle on success and material metadata changes`() = runTest {
        fillCart()

        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Submit with failure
        fakeManualRepo.shouldFailWithNetworkError = true
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val firstId = fakeManualRepo.lastRequest?.requestId
        assertNotNull(firstId)

        // 2. Logical equivalence -> NO rotation
        viewModel.updatePickupName("  Aldo  ") // surrounding whitespace
        viewModel.updatePaymentMethod(PaymentMethod.CASH)
        viewModel.updateCashDenomination(BigDecimal("100"))
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        val secondId = fakeManualRepo.lastRequest?.requestId
        assertNotEquals(firstId, secondId) // Wait, payment changed! So it DOES rotate here

        fakeManualRepo.lastRequest = null

        // Set cash to exactly 100
        viewModel.updateCashDenomination(BigDecimal("100.00"))
        viewModel.cobrarOrden() // Should not rotate, logical equivalence (100 vs 100.00)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(secondId, fakeManualRepo.lastRequest?.requestId)

        // 3. Material metadata change -> Rotation
        viewModel.updatePickupName("Aldo V")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        val thirdId = fakeManualRepo.lastRequest?.requestId
        assertNotEquals(secondId, thirdId)

        // 4. Failed/No-op cart change -> NO rotation
        viewModel.removeFromCart(ConfiguredProduct(id = "doesntexist", menuItemId = 999L, name = "", quantity = 1, baseUnitPrice = BigDecimal.ZERO, unitTotal = BigDecimal.ZERO, total = BigDecimal.ZERO, groups = emptyList()))
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(thirdId, fakeManualRepo.lastRequest?.requestId)

        // 5. Success -> clears cart and ID
        fakeManualRepo.shouldFailWithNetworkError = false
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PosUiState.Success)
        assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isEmpty())
    }

    @Test
    fun `test validations prevent repository calls and keep cart intact`() = runTest {
        // --- PICKUP ---
        // null/empty or too-short effective pickup name
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName(" A ") // effectively "A"
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeManualRepo.submitCount)
        assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isNotEmpty())
        assertTrue((viewModel.uiState.value as PosUiState.Success).checkoutState is CheckoutState.Error)

        // more than 120 trimmed characters
        viewModel.resetCheckoutState()
        viewModel.updatePickupName("A".repeat(121))
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, fakeManualRepo.submitCount)
        assertTrue((viewModel.uiState.value as PosUiState.Success).checkoutState is CheckoutState.Error)

        // --- DELIVERY ---
        // empty/too-short effective delivery address
        viewModel.resetCheckoutState()
        viewModel.updateFulfillmentType(FulfillmentType.DELIVERY)
        viewModel.updateDeliveryAddress(" 123 ") // effectively 3 chars
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, fakeManualRepo.submitCount)
        assertTrue((viewModel.uiState.value as PosUiState.Success).checkoutState is CheckoutState.Error)

        // more than 500 trimmed characters
        viewModel.resetCheckoutState()
        viewModel.updateDeliveryAddress("B".repeat(501))
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, fakeManualRepo.submitCount)
        assertTrue((viewModel.uiState.value as PosUiState.Success).checkoutState is CheckoutState.Error)

        // --- CARD ---
        // CARD + DELIVERY rejected
        viewModel.resetCheckoutState()
        viewModel.updateDeliveryAddress("123 Fake St")
        viewModel.updatePaymentMethod(PaymentMethod.CARD)
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, fakeManualRepo.submitCount)
        assertTrue((viewModel.uiState.value as PosUiState.Success).checkoutState is CheckoutState.Error)

        // CARD + PICKUP accepted
        viewModel.resetCheckoutState()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, fakeManualRepo.submitCount)

        // --- CASH ---
        fillCart() // cart was cleared on previous success
        viewModel.updatePaymentMethod(PaymentMethod.CASH)
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")

        // PICKUP + CASH requires NO denomination
        viewModel.updateCashDenomination(null)
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, fakeManualRepo.submitCount) // Success!

        // --- DELIVERY + CASH ---
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.DELIVERY)
        viewModel.updateDeliveryAddress("123 Fake St")

        // null denomination rejected
        viewModel.updateCashDenomination(null)
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, fakeManualRepo.submitCount) // still 2

        // zero rejected
        viewModel.resetCheckoutState()
        viewModel.updateCashDenomination(BigDecimal.ZERO)
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, fakeManualRepo.submitCount)

        // negative rejected
        viewModel.resetCheckoutState()
        viewModel.updateCashDenomination(BigDecimal("-10.00"))
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, fakeManualRepo.submitCount)

        // positive accepted
        viewModel.resetCheckoutState()
        viewModel.updateCashDenomination(BigDecimal("10.00"))
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, fakeManualRepo.submitCount)
    }

    @Test
    fun `test TRANSFER and CARD clear cashDenomination`() = runTest {
        // TRANSFER
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updateCashDenomination(BigDecimal("500.00"))
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(fakeManualRepo.lastRequest)
        assertNull(fakeManualRepo.lastRequest?.cashDenomination)

        // CARD
        fillCart()
        viewModel.updatePaymentMethod(PaymentMethod.CARD)
        // cash denom still theoretically 500 in state
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(fakeManualRepo.lastRequest)
        assertNull(fakeManualRepo.lastRequest?.cashDenomination)
    }

    @Test
    fun `test authoritative server result overrides local total`() = runTest {
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        testDispatcher.scheduler.advanceUntilIdle()

        fakeManualRepo.mockedResponseTotal = BigDecimal("12345.67")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PosUiState.Success
        val successState = state.checkoutState as CheckoutState.Success
        assertEquals(BigDecimal("12345.67"), successState.response.total)
    }

    @Test
    fun `test duplicate submit race condition`() = runTest {
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        testDispatcher.scheduler.advanceUntilIdle()

        fakeManualRepo.delaySubmit = true

        // Call twice immediately
        viewModel.cobrarOrden()
        viewModel.cobrarOrden()

        // Let coroutines start, but repo delays
        testDispatcher.scheduler.advanceTimeBy(100)

        // Assert Loading
        assertTrue((viewModel.uiState.value as PosUiState.Success).checkoutState is CheckoutState.Loading)

        // Finish repo
        fakeManualRepo.delaySubmit = false
        testDispatcher.scheduler.advanceUntilIdle()

        // Only one submission should have happened
        assertEquals(1, fakeManualRepo.submitCount)
    }

    @Test
    fun `test error mapping safe UX`() = runTest {
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        testDispatcher.scheduler.advanceUntilIdle()

        val cases = listOf(
            "ORDER_INVALID" to "Datos de orden inválidos. Revisa la información.",
            "ORDER_IDEMPOTENCY_CONFLICT" to "Conflicto de idempotencia. Esta orden puede haber sido procesada parcialmente.",
            "ORDER_MENU_ITEM_NOT_FOUND" to "Un producto ya no existe en el catálogo.",
            "ORDER_MENU_ITEM_UNAVAILABLE" to "Un producto seleccionado no está disponible.",
            "ORDER_CONFIGURATION_INVALID" to "Configuración de producto inválida.",
            "ORDER_PROMOTION_CONFLICT" to "La promoción cambió o dejó de estar disponible. Revisa la orden e intenta de nuevo.",
            "PROMOTION_REWARD_INVALID" to "La promoción cambió o dejó de estar disponible. Revisa la orden e intenta de nuevo.",
            "ORDER_FORBIDDEN_OPERATION" to "No tienes permisos para realizar esta operación.",
            "AUTH_FORBIDDEN" to "No tienes permisos para realizar esta operación.",
            "UNKNOWN_CODE_ABC" to "Error del servidor. La orden no pudo confirmarse. Intenta de nuevo."
        )

        for ((code, expectedMessage) in cases) {
            viewModel.resetCheckoutState()
            fakeManualRepo.shouldFailWithApiError = ApiException(code, "Backend Message")
            viewModel.cobrarOrden()
            testDispatcher.scheduler.advanceUntilIdle()

            val error = (viewModel.uiState.value as PosUiState.Success).checkoutState as CheckoutState.Error
            assertEquals(expectedMessage, error.message)
            assertFalse(error.message.contains(code))
            assertFalse(error.message.contains("Backend Message"))
            assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isNotEmpty())
        }

        // Generic Exception -> Network Error
        viewModel.resetCheckoutState()
        fakeManualRepo.shouldFailWithApiError = null
        fakeManualRepo.shouldFailWithNetworkError = true
        fakeManualRepo.shouldFailWithUnexpectedError = false
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        var error = (viewModel.uiState.value as PosUiState.Success).checkoutState as CheckoutState.Error
        assertEquals("Error de red: La orden no pudo confirmarse. Intenta de nuevo.", error.message)
        assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isNotEmpty())

        // Unexpected Exception -> Generic Error
        viewModel.resetCheckoutState()
        fakeManualRepo.shouldFailWithNetworkError = false
        fakeManualRepo.shouldFailWithUnexpectedError = true
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        error = (viewModel.uiState.value as PosUiState.Success).checkoutState as CheckoutState.Error
        assertEquals("Error inesperado al procesar la orden. Intenta de nuevo.", error.message)
        assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isNotEmpty())
    }

    @Test
    fun `test CREATED and ALREADY_CREATED states clear cart`() = runTest {
        // ALREADY_CREATED
        fillCart()
        viewModel.updateFulfillmentType(FulfillmentType.PICKUP)
        viewModel.updatePickupName("Aldo")
        viewModel.updatePaymentMethod(PaymentMethod.TRANSFER)
        testDispatcher.scheduler.advanceUntilIdle()

        fakeManualRepo.mockedResult = OrderResult.ALREADY_CREATED
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isEmpty())
        assertEquals(OrderResult.ALREADY_CREATED, ((viewModel.uiState.value as PosUiState.Success).checkoutState as CheckoutState.Success).response.result)

        // CREATED
        fillCart()
        fakeManualRepo.mockedResult = OrderResult.CREATED
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as PosUiState.Success).currentCart.isEmpty())
    }
}

class FakeManualPosOrderRepository : IManualPosOrderRepository {
    var lastRequest: ManualPosOrderRequest? = null
    var shouldFailWithNetworkError = false
    var shouldFailWithUnexpectedError = false
    var shouldFailWithApiError: ApiException? = null
    var submitCount = 0
    var delaySubmit = false
    var mockedResponseTotal = BigDecimal("150.00")
    var mockedResult = OrderResult.CREATED

    override suspend fun submitOrder(request: ManualPosOrderRequest): ManualPosOrderResponse {
        submitCount++
        lastRequest = request

        if (delaySubmit) {
            delay(1000)
        }

        if (shouldFailWithNetworkError) throw java.io.IOException("Network fail")
        if (shouldFailWithUnexpectedError) throw Exception("Unexpected fail")
        if (shouldFailWithApiError != null) throw shouldFailWithApiError!!

        return ManualPosOrderResponse(
            id = 100L,
            requestId = request.requestId,
            result = mockedResult,
            orderSource = "POS",
            createdByUserId = 1L,
            fulfillmentType = request.fulfillmentType,
            paymentMethod = request.paymentMethod,
            deliveryAddress = request.deliveryAddress,
            pickupName = request.pickupName,
            cashDenomination = request.cashDenomination,
            status = "PREPARING",
            createdAt = Instant.now(),
            lines = emptyList(),
            total = mockedResponseTotal
        )
    }
}

class FakeMenuRepository : IMenuRepository {
    override suspend fun refreshCatalog(standaloneOnly: Boolean?) {}
    override fun observeAll(): Flow<List<MenuItem>> = flowOf(emptyList())
    override fun observeActive(): Flow<List<MenuItem>> = flowOf(emptyList())
    override fun observeActiveCategories(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun getProducts(): List<MenuItem> = emptyList()
    override suspend fun getCategories(): List<String> = emptyList()
    override suspend fun createProduct(request: MenuItemCreateRequestDto): MenuItemResponse = TODO()
    override suspend fun updateProduct(id: Long, request: MenuItemUpdateRequestDto): MenuItemResponse = TODO()
    override suspend fun deleteProduct(id: Long) {}
    override suspend fun setActive(id: Long, activo: Boolean) {}
    override suspend fun getConfiguration(menuItemId: Long): ConfigurationResponseDto = TODO()
    override suspend fun getTags(): List<CatalogTagDto> = emptyList()
    override suspend fun createTag(tag: TagCreateRequestDto): CatalogTagDto = TODO()
    override suspend fun updateTag(id: Long, tag: TagUpdateRequestDto): CatalogTagDto = TODO()
    override suspend fun deleteTag(id: Long) {}

    override suspend fun quoteItem(menuItemId: Long, request: ItemQuoteRequestDto): ItemQuoteResponseDto {
        return ItemQuoteResponseDto(menuItemId, "Mock", request.quantity, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

class FakePromotionRepository : IPromotionRepository {
    override fun observePromotions(): Flow<List<Promotion>> = flowOf(emptyList())
    override suspend fun getPromotions(): List<Promotion> = emptyList()
    override suspend fun getActivePromotions(): List<Promotion> = emptyList()
    override suspend fun getPromotion(id: Long): Promotion? = null
    override suspend fun createPromotion(promotion: Promotion): Promotion = TODO()
    override suspend fun updatePromotion(promotion: Promotion): Promotion = TODO()
    override suspend fun archivePromotion(id: Long) {}

    override suspend fun quoteCart(cart: List<ConfiguredProduct>): OrderPricingPreview {
        return OrderPricingPreview(BigDecimal.ZERO, emptyList(), emptyList(), BigDecimal.ZERO)
    }
}

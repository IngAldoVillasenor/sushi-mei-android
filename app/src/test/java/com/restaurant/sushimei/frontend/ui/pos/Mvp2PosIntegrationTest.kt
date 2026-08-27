package com.restaurant.sushimei.frontend.ui.pos
import kotlinx.coroutines.launch

import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class Mvp2PosIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var menuRepository: IMenuRepository
    private lateinit var manualPosOrderRepository: IManualPosOrderRepository
    private lateinit var promotionRepository: IPromotionRepository
    private lateinit var printManager: com.restaurant.sushimei.frontend.PrintManager
    private lateinit var printJobRepository: com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        menuRepository = mockk(relaxed = true)
        manualPosOrderRepository = mockk(relaxed = true)
        promotionRepository = mockk(relaxed = true)
        printManager = mockk(relaxed = true)
        printJobRepository = mockk(relaxed = true)
        viewModel = PosViewModel(
            menuRepository,
            manualPosOrderRepository,
            promotionRepository,
            printManager,
            printJobRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCheckoutManualOnly() = runTest {
        val reqSlot = io.mockk.slot<com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest>()
        coEvery { manualPosOrderRepository.submitOrder(capture(reqSlot)) } returns mockk(relaxed = true)

        viewModel.addManualLine("Test", BigDecimal("150.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(reqSlot.captured.lines.isEmpty())
        assertEquals(1, reqSlot.captured.manualLines.size)
        assertEquals("Test", reqSlot.captured.manualLines[0].description)
    }

    @Test
    fun testCheckoutMixed() = runTest {
        val reqSlot = io.mockk.slot<com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest>()
        coEvery { manualPosOrderRepository.submitOrder(capture(reqSlot)) } returns mockk(relaxed = true)
        coEvery { menuRepository.quoteItem(any(), any()) } returns com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto(menuItemId = 1L, name = "T", quantity = 1, baseUnitPrice = java.math.BigDecimal.TEN, baseTotal = java.math.BigDecimal.TEN, unitAdjustmentTotal = java.math.BigDecimal.ZERO, unitTotal = java.math.BigDecimal.TEN, total = java.math.BigDecimal.TEN)
        coEvery { promotionRepository.quoteCart(any()) } returns com.restaurant.sushimei.frontend.data.model.OrderPricingPreview(java.math.BigDecimal.TEN, emptyList(), emptyList(), emptyList(), java.math.BigDecimal.TEN)

        viewModel.addToCart(com.restaurant.sushimei.frontend.data.model.MenuItem(id = 1L, nombre = "T", categoria = "T", precio = java.math.BigDecimal.TEN))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addManualLine("Test", BigDecimal("150.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, reqSlot.captured.lines.size)
        assertEquals(1, reqSlot.captured.manualLines.size)
    }

    @Test
    fun testCheckoutClearsCart() = runTest {
        val collectJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testDispatcher.scheduler)).launch {
            viewModel.uiState.collect { }
        }
        coEvery { manualPosOrderRepository.submitOrder(any()) } returns com.restaurant.sushimei.frontend.data.model.ManualPosOrderResponse(
            id = 1L,
            requestId = "req",
            result = com.restaurant.sushimei.frontend.data.model.OrderResult.CREATED,
            orderSource = "POS",
            createdByUserId = 1L,
            fulfillmentType = com.restaurant.sushimei.frontend.data.model.FulfillmentType.PICKUP,
            paymentMethod = com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH,
            deliveryAddress = null,
            pickupName = "Test Name",
            cashDenomination = null,
            lines = emptyList(),
            total = java.math.BigDecimal.TEN,
            status = "OPEN",
            createdAt = java.time.Instant.parse("2026-08-26T10:00:00Z")
        )
        viewModel.addManualLine("Test", BigDecimal("150.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val manualCartField = PosViewModel::class.java.getDeclaredField("_manualCart")
        manualCartField.isAccessible = true
        val manualCartFlow = manualCartField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<List<com.restaurant.sushimei.frontend.data.model.ManualCartLine>>
        val currentCartField = PosViewModel::class.java.getDeclaredField("_currentCart")
        currentCartField.isAccessible = true
        val currentCartFlow = currentCartField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<List<com.restaurant.sushimei.frontend.data.model.QuotedCartLine>>
        assertTrue(manualCartFlow.value.isEmpty())
        assertTrue(currentCartFlow.value.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun testUnchangedRetryPreservesRequestId() = runTest {
        viewModel.addManualLine("Test", BigDecimal("150.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { manualPosOrderRepository.submitOrder(any()) } throws Exception("Network")
        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val reqSlot = io.mockk.slot<com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest>()
        coEvery { manualPosOrderRepository.submitOrder(capture(reqSlot)) } returns mockk(relaxed = true)

        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()

        val reqId = reqSlot.captured.requestId
        assertTrue(reqId.isNotBlank())
    }

    @Test
    fun testManualMutationInvalidatesRequestId() = runTest {
        viewModel.addManualLine("Test", BigDecimal("150.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        val reqSlot1 = io.mockk.slot<com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest>()
        coEvery { manualPosOrderRepository.submitOrder(capture(reqSlot1)) } throws Exception("Network")
        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        val reqId1 = reqSlot1.captured.requestId

        viewModel.addManualLine("Test2", BigDecimal("50.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        val reqSlot2 = io.mockk.slot<com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest>()
        coEvery { manualPosOrderRepository.submitOrder(capture(reqSlot2)) } returns mockk(relaxed = true)
        viewModel.updatePickupName("Test Name")
        viewModel.cobrarOrden()
        testDispatcher.scheduler.advanceUntilIdle()
        val reqId2 = reqSlot2.captured.requestId

        assertNotEquals(reqId1, reqId2)
    }
}

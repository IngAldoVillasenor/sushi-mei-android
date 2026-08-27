package com.restaurant.sushimei.frontend.ui.pos

import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse
import com.restaurant.sushimei.frontend.data.model.OpenSaleRequest
import com.restaurant.sushimei.frontend.data.model.OpenSaleResponse
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import com.restaurant.sushimei.frontend.data.repository.IManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class Mvp2PosViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var menuRepository: IMenuRepository
    private lateinit var manualPosOrderRepository: IManualPosOrderRepository
    private lateinit var promotionRepository: IPromotionRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        menuRepository = mockk(relaxed = true)
        manualPosOrderRepository = mockk(relaxed = true)
        promotionRepository = mockk(relaxed = true)
        val printManager = mockk<com.restaurant.sushimei.frontend.PrintManager>(relaxed = true)
        val printJobRepository = mockk<com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository>(relaxed = true)
        coEvery { printJobRepository.observeAllJobs() } returns flowOf(emptyList())


        coEvery { menuRepository.observeActive() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { promotionRepository.observePromotions() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        viewModel = PosViewModel(

            menuRepository, manualPosOrderRepository, promotionRepository, printManager, printJobRepository
        )
    }

    @Test
    fun testCartIdentityDistinctOmissionsAndNotes() = runTest {
        val baseProduct = ConfiguredProduct(
            menuItemId = 1L, name = "Ramen", quantity = 1, baseUnitPrice = BigDecimal.TEN, unitTotal = BigDecimal.TEN, total = BigDecimal.TEN,
            omittedComponents = emptyList(), note = null
        )
        viewModel.addConfiguredProduct(baseProduct)

        val productWithOmission = baseProduct.copy(omittedComponents = listOf(
            DefaultComponentResponse(1L, "CEB", "Cebollin", null, true, true, 1, true)
        ))
        viewModel.addConfiguredProduct(productWithOmission)

        val productWithNote = baseProduct.copy(note = "Extra hot")
        viewModel.addConfiguredProduct(productWithNote)

        viewModel.addConfiguredProduct(baseProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as? PosUiState.Success)?.currentCart ?: emptyList()
        assertEquals(3, cart.size)
        assertEquals(2, cart[0].quantity)
        assertEquals(1, cart[1].quantity)
        assertEquals(1, cart[2].quantity)
    }

    @Test
    fun testSubmitOpenSaleSuccess() = runTest {
        val slot = slot<OpenSaleRequest>()
        coEvery { manualPosOrderRepository.createOpenSale(capture(slot)) } returns
            OpenSaleResponse(1L, "reqId", "CREATED", "OPEN_SALE", 1L, "Venta", 1, BigDecimal.TEN, BigDecimal.TEN, PaymentMethod.CASH, BigDecimal("20.00"), "COMPLETED", "reqId")

        viewModel.submitOpenSale("Venta", BigDecimal.TEN, PaymentMethod.CASH, BigDecimal("20.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((viewModel.uiState.value as? PosUiState.Success)?.checkoutState is CheckoutState.OpenSaleSuccess)
        assertTrue(slot.isCaptured)
        assertEquals("Venta", slot.captured.description)
        assertEquals(PaymentMethod.CASH, slot.captured.paymentMethod)
        assertEquals(BigDecimal("20.00"), slot.captured.cashDenomination)
    }

    @Test
    fun testSubmitOpenSaleBackendError() = runTest {
        coEvery { manualPosOrderRepository.createOpenSale(any()) } throws Exception("BusinessDay missing")
        viewModel.submitOpenSale("Venta", BigDecimal.TEN, PaymentMethod.CASH, BigDecimal("20.00"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as? PosUiState.Success)?.checkoutState is CheckoutState.Error)
    }

    @Test
    fun testSubmitOpenSaleCardSuccess() = runTest {
        val slot = slot<OpenSaleRequest>()
        coEvery { manualPosOrderRepository.createOpenSale(capture(slot)) } returns
            OpenSaleResponse(1L, "reqId", "CREATED", "OPEN_SALE", 1L, "Venta", 1, BigDecimal.TEN, BigDecimal.TEN, PaymentMethod.CARD, null, "COMPLETED", "reqId")

        // Intentionally sending cashDenomination, but the ViewModel should null it out for CARD
        viewModel.submitOpenSale("Venta", BigDecimal.TEN, PaymentMethod.CARD, BigDecimal("20.00"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((viewModel.uiState.value as? PosUiState.Success)?.checkoutState is CheckoutState.OpenSaleSuccess)
        assertTrue(slot.isCaptured)
        assertEquals("Venta", slot.captured.description)
        assertEquals(PaymentMethod.CARD, slot.captured.paymentMethod)
        assertEquals(null, slot.captured.cashDenomination)
    }

    @Test
    fun testSubmitOpenSaleTransferSuccess() = runTest {
        val slot = slot<OpenSaleRequest>()
        coEvery { manualPosOrderRepository.createOpenSale(capture(slot)) } returns
            OpenSaleResponse(1L, "reqId", "CREATED", "OPEN_SALE", 1L, "Venta", 1, BigDecimal.TEN, BigDecimal.TEN, PaymentMethod.TRANSFER, null, "COMPLETED", "reqId")

        // No cash denomination provided for TRANSFER
        viewModel.submitOpenSale("Venta", BigDecimal.TEN, PaymentMethod.TRANSFER, null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((viewModel.uiState.value as? PosUiState.Success)?.checkoutState is CheckoutState.OpenSaleSuccess)
        assertTrue(slot.isCaptured)
        assertEquals(PaymentMethod.TRANSFER, slot.captured.paymentMethod)
        assertEquals(null, slot.captured.cashDenomination)
    }

}

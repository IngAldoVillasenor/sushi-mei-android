package com.restaurant.sushimei.frontend.ui.pos

import com.restaurant.sushimei.frontend.data.model.*
import com.restaurant.sushimei.frontend.data.repository.IManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var menuRepository: IMenuRepository
    private lateinit var manualPosOrderRepository: IManualPosOrderRepository
    private lateinit var promotionRepository: IPromotionRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        menuRepository = mockk()
        manualPosOrderRepository = mockk()
        promotionRepository = mockk()

        val catalogItemSimple = MenuItem(
            id = 1,
            nombre = "Simple Item",
            categoria = "Rolls",
            precio = BigDecimal("100.00"),
            requiresConfiguration = false
        )

        val catalogItemConfigurable = MenuItem(
            id = 2,
            nombre = "Configurable Item",
            categoria = "Rolls",
            precio = BigDecimal("0.00"),
            requiresConfiguration = true,
            pricingMode = ItemPricingMode.SELECTION_SUM
        )

        coEvery { menuRepository.observeActiveCategories() } returns flowOf(listOf("Rolls"))
        coEvery { menuRepository.observeActive() } returns flowOf(listOf(catalogItemSimple, catalogItemConfigurable))
        coEvery { menuRepository.refreshCatalog(any()) } returns Unit

        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "Simple Item", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("100.00"), unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"), total = BigDecimal("100.00"),
            groups = emptyList()
        )

        viewModel = PosViewModel(menuRepository, manualPosOrderRepository, promotionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `incrementCartItem routes simple item to direct add`() = runTest {
        val simpleProduct = ConfiguredProduct(
            menuItemId = 1,
            name = "Simple Item",
            quantity = 1,
            baseUnitPrice = BigDecimal("100.00"),
            unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00"),
            groups = emptyList()
        )

        var calledRequiresConfig = false
        viewModel.incrementCartItem(simpleProduct) {
            calledRequiresConfig = true
        }
        testScheduler.advanceUntilIdle()

        assertTrue("Configurator callback should not be invoked", !calledRequiresConfig)

        val state = viewModel.uiState.value
        if (state is PosUiState.Success) {
            val cart = state.currentCart
            assertEquals("Item should be added to cart directly", 1, cart.size)
            assertEquals("Quantity should be updated", 1, cart[0].quantity)
        } else {
            org.junit.Assert.fail("Expected PosUiState.Success")
        }
    }

    @Test
    fun `incrementCartItem routes configurable item to configurator callback`() = runTest {
        val configProduct = ConfiguredProduct(
            menuItemId = 2,
            name = "Configurable Item",
            quantity = 1,
            baseUnitPrice = BigDecimal("0.00"),
            unitTotal = BigDecimal("50.00"),
            total = BigDecimal("50.00"),
            groups = emptyList()
        )

        var calledRequiresConfig = false
        viewModel.incrementCartItem(configProduct) {
            calledRequiresConfig = true
        }
        testScheduler.advanceUntilIdle()

        assertTrue("Configurator callback MUST be invoked for requiresConfiguration=true", calledRequiresConfig)

        val state = viewModel.uiState.value
        if (state is PosUiState.Success) {
            val cart = state.currentCart
            assertEquals("Item should NOT be added to cart directly", 0, cart.size)
        } else {
            org.junit.Assert.fail("Expected PosUiState.Success")
        }
    }
}

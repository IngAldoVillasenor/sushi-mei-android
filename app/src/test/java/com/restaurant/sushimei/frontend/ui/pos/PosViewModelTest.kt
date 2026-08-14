package com.restaurant.sushimei.frontend.ui.pos

import com.restaurant.sushimei.frontend.data.api.ApiException
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
        coEvery { promotionRepository.getActivePromotions() } returns emptyList()
        coEvery { promotionRepository.quoteCart(any()) } returns OrderPricingPreview(
            subtotal = BigDecimal.ZERO,
            total = BigDecimal.ZERO
        )

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

    @Test
    fun `active bogo promotion creates one authoritative line with reward ordinal`() = runTest {
        val promotion = Promotion(
            id = 20L,
            name = "Jueves 2x1",
            active = true,
            priority = 100,
            schedule = PromotionSchedule(daysOfWeek = setOf(4), allDay = true),
            targets = listOf(PromotionTarget(PromotionTargetType.ITEM, 1L, "Simple Item")),
            benefit = PromotionBenefit.BuyXGetYSameItem(
                buyQuantity = 1,
                rewardQuantity = 1,
                repeat = true
            )
        )
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)

        testScheduler.advanceUntilIdle()
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val stateWithPromotions = viewModel.uiState.value as PosUiState.Success
        assertEquals(listOf(promotion), stateWithPromotions.activePromotions)
        assertEquals(listOf(1L), viewModel.eligibleProducts(promotion).map { it.id })

        val menuItem = stateWithPromotions.allProducts.first { it.id == 1L }
        viewModel.addPromotionBundle(promotion, menuItem)
        testScheduler.advanceUntilIdle()

        val line = (viewModel.uiState.value as PosUiState.Success).currentCart.single()
        assertEquals(20L, line.promotionSelection?.promotionId)
        assertEquals(1, line.promotionSelection?.rewardConfigurations?.single()?.rewardOrdinal)
        assertTrue(line.promotionSelection?.rewardConfigurations?.single()?.groups?.isEmpty() == true)
    }

    @Test
    fun `stale promotion is removed when authoritative quote rejects it`() = runTest {
        val promotion = Promotion(
            id = 20L,
            name = "Jueves 2x1",
            active = true,
            priority = 100,
            schedule = PromotionSchedule(daysOfWeek = setOf(4), allDay = true),
            targets = listOf(PromotionTarget(PromotionTargetType.ITEM, 1L, "Simple Item")),
            benefit = PromotionBenefit.BuyXGetYSameItem(
                buyQuantity = 1,
                rewardQuantity = 1,
                repeat = true
            )
        )
        testScheduler.advanceUntilIdle()
        coEvery { promotionRepository.getActivePromotions() } returnsMany listOf(
            listOf(promotion),
            emptyList()
        )
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()
        coEvery { promotionRepository.quoteCart(any()) } throws ApiException(
            "PROMOTION_REWARD_INVALID",
            "La promoción ya no aplica"
        )

        val menuItem = (viewModel.uiState.value as PosUiState.Success).allProducts.first { it.id == 1L }
        viewModel.addPromotionBundle(promotion, menuItem)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PosUiState.Success
        assertTrue(state.currentCart.isEmpty())
        assertTrue(state.activePromotions.isEmpty())
        assertEquals(
            "Una promoción dejó de estar disponible y se retiró de la orden.",
            state.promotionLoadError
        )
    }
}

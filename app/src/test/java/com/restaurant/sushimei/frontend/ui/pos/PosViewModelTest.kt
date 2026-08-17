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
import org.junit.Assert.assertNotNull
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
            benefit = PromotionBenefit.BuyXGetY(type = "BUY_X_GET_Y_SAME_ITEM",
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
            benefit = PromotionBenefit.BuyXGetY(type = "BUY_X_GET_Y_SAME_ITEM",
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

    // -------------------------------------------------------------------------
    // BUY_X_GET_Y_ELIGIBLE_ITEM tests
    // -------------------------------------------------------------------------

    @Test
    fun `BuyXGetY ELIGIBLE_ITEM is decoded with correct type string`() {
        val benefit = PromotionBenefit.BuyXGetY.validated(
            type = PromotionBenefit.BuyXGetY.ELIGIBLE_ITEM,
            buyQuantity = 1,
            rewardQuantity = 1,
            repeat = true
        )
        assertEquals(PromotionBenefit.BuyXGetY.ELIGIBLE_ITEM, benefit.type)
        assertTrue(PromotionBenefit.BuyXGetY.isEligibleItemVariant(benefit.type))
    }

    @Test
    fun `BuyXGetY SAME_ITEM is decoded with correct type string and isEligibleItemVariant returns false`() {
        val benefit = PromotionBenefit.BuyXGetY.validated(
            type = PromotionBenefit.BuyXGetY.SAME_ITEM,
            buyQuantity = 1,
            rewardQuantity = 1,
            repeat = false
        )
        assertEquals(PromotionBenefit.BuyXGetY.SAME_ITEM, benefit.type)
        assertTrue(!PromotionBenefit.BuyXGetY.isEligibleItemVariant(benefit.type))
    }

    @Test
    fun `BuyXGetY validated rejects unsupported type strings`() {
        var threw = false
        try {
            PromotionBenefit.BuyXGetY.validated("UNKNOWN_TYPE", 1, 1, false)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("validated() must throw for unsupported type", threw)
    }

    @Test
    fun `ELIGIBLE_ITEM addPromotionBundle allows different menuItemId for purchased and reward`() = runTest {
        // California (id=1) purchased, Empanizado (id=3) as reward – different menuItemIds.
        val empanizado = MenuItem(
            id = 3,
            nombre = "Empanizado",
            categoria = "Rolls",
            precio = BigDecimal("130.00"),
            requiresConfiguration = true
        )
        coEvery { menuRepository.observeActive() } returns flowOf(listOf(
            MenuItem(id = 1, nombre = "California", categoria = "Rolls", precio = BigDecimal("100.00"), requiresConfiguration = true),
            empanizado
        ))
        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "California", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("100.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00"), groups = emptyList()
        )

        val promotion = Promotion(
            id = 99L,
            name = "Promo Jueves",
            active = true,
            priority = 100,
            schedule = PromotionSchedule(daysOfWeek = setOf(4), allDay = true),
            targets = listOf(PromotionTarget(PromotionTargetType.ITEM, 1L, "California")),
            benefit = PromotionBenefit.BuyXGetY.validated(
                type = PromotionBenefit.BuyXGetY.ELIGIBLE_ITEM,
                buyQuantity = 1,
                rewardQuantity = 1,
                repeat = false
            )
        )
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val california = (viewModel.uiState.value as PosUiState.Success).allProducts.first { it.id == 1L }
        val purchasedProduct = ConfiguredProduct(
            menuItemId = 1,
            name = "California",
            quantity = 1,
            baseUnitPrice = BigDecimal("100.00"),
            unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00")
        )
        val rewardProduct = ConfiguredProduct(
            menuItemId = 3, // Empanizado — different from purchased
            name = "Empanizado",
            quantity = 1,
            baseUnitPrice = BigDecimal("130.00"),
            unitTotal = BigDecimal("130.00"),
            total = BigDecimal("130.00")
        )

        viewModel.addPromotionBundle(
            promotion = promotion,
            menuItem = california,
            purchasedProduct = purchasedProduct,
            rewardProducts = listOf(rewardProduct)
        )
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals("Cart must have one line", 1, cart.size)
        val line = cart.single()
        assertEquals("Purchased item must be California", 1L, line.menuItemId)
        val rewardConfig = line.promotionSelection?.rewardConfigurations?.single()
        assertNotNull("Reward configuration must be present", rewardConfig)
        assertEquals("Reward menuItemId must be Empanizado (3)", 3L, rewardConfig?.menuItemId)
        assertEquals("Reward ordinal must be 1", 1, rewardConfig?.rewardOrdinal)
    }

    @Test
    fun `ELIGIBLE_ITEM reward menuItemId travels from ConfiguredProduct to ConfiguredRewardConfiguration`() = runTest {
        val promotion = Promotion(
            id = 50L, name = "Test Eligible", active = true, priority = 1,
            schedule = PromotionSchedule(daysOfWeek = setOf(1), allDay = true),
            targets = listOf(PromotionTarget(PromotionTargetType.ITEM, 1L, "Simple Item")),
            benefit = PromotionBenefit.BuyXGetY.validated(
                type = PromotionBenefit.BuyXGetY.ELIGIBLE_ITEM,
                buyQuantity = 1, rewardQuantity = 1, repeat = false
            )
        )
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val menuItem = (viewModel.uiState.value as PosUiState.Success).allProducts.first { it.id == 1L }
        val rewardProduct = ConfiguredProduct(
            menuItemId = 99L, // distinct reward item id
            name = "Reward Item",
            quantity = 1,
            baseUnitPrice = BigDecimal("50.00"),
            unitTotal = BigDecimal("50.00"),
            total = BigDecimal("50.00")
        )

        viewModel.addPromotionBundle(
            promotion = promotion,
            menuItem = menuItem,
            rewardProducts = listOf(rewardProduct)
        )
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals(1, cart.size)
        val reward = cart.single().promotionSelection?.rewardConfigurations?.single()
        assertEquals(
            "menuItemId 99 must be forwarded to ConfiguredRewardConfiguration",
            99L, reward?.menuItemId
        )
    }

    @Test
    fun `ELIGIBLE_ITEM reward groups are independent from purchased item groups`() = runTest {
        val promotion = Promotion(
            id = 51L, name = "Eligible Groups", active = true, priority = 1,
            schedule = PromotionSchedule(daysOfWeek = setOf(1), allDay = true),
            targets = listOf(PromotionTarget(PromotionTargetType.ITEM, 1L, "Simple Item")),
            benefit = PromotionBenefit.BuyXGetY.validated(
                type = PromotionBenefit.BuyXGetY.ELIGIBLE_ITEM,
                buyQuantity = 1, rewardQuantity = 1, repeat = false
            )
        )
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val menuItem = (viewModel.uiState.value as PosUiState.Success).allProducts.first { it.id == 1L }
        val purchasedGroups = listOf(ConfiguredGroup(1L, "Proteína", listOf(
            ConfiguredSelection(10L, "Camarón", 1, BigDecimal("20.00"), BigDecimal.ZERO)
        )))
        val rewardGroups = listOf(ConfiguredGroup(2L, "Cobertura", listOf(
            ConfiguredSelection(20L, "Empanizado", 1, BigDecimal("15.00"), BigDecimal.ZERO)
        )))

        val purchasedProduct = ConfiguredProduct(menuItemId = 1L, name = "Simple Item", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), unitTotal = BigDecimal("100.00"), total = BigDecimal("100.00"),
            groups = purchasedGroups)
        val rewardProduct = ConfiguredProduct(menuItemId = 77L, name = "Other Item", quantity = 1,
            baseUnitPrice = BigDecimal("80.00"), unitTotal = BigDecimal("80.00"), total = BigDecimal("80.00"),
            groups = rewardGroups)

        viewModel.addPromotionBundle(
            promotion = promotion,
            menuItem = menuItem,
            purchasedProduct = purchasedProduct,
            rewardProducts = listOf(rewardProduct)
        )
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals(1, cart.size)
        val line = cart.single()
        // Purchased line must have its own groups
        assertEquals("Purchased groups must be preserved", purchasedGroups, line.groups)
        val rewardConfig = line.promotionSelection?.rewardConfigurations?.single()
        // Reward config must carry reward item's groups, not the purchased item's
        assertEquals("Reward groups must be independent", rewardGroups, rewardConfig?.groups)
    }

    @Test
    fun `SAME_ITEM addPromotionBundle still rejects reward with different menuItemId`() = runTest {
        val promotion = Promotion(
            id = 52L, name = "Same Item Promo", active = true, priority = 1,
            schedule = PromotionSchedule(daysOfWeek = setOf(1), allDay = true),
            targets = listOf(PromotionTarget(PromotionTargetType.ITEM, 1L, "Simple Item")),
            benefit = PromotionBenefit.BuyXGetY.validated(
                type = PromotionBenefit.BuyXGetY.SAME_ITEM,
                buyQuantity = 1, rewardQuantity = 1, repeat = false
            )
        )
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val menuItem = (viewModel.uiState.value as PosUiState.Success).allProducts.first { it.id == 1L }
        val wrongReward = ConfiguredProduct(
            menuItemId = 999L, // wrong – different from menuItem.id=1
            name = "Wrong Item",
            quantity = 1,
            baseUnitPrice = BigDecimal("100.00"),
            unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00")
        )

        viewModel.addPromotionBundle(
            promotion = promotion,
            menuItem = menuItem,
            purchasedProduct = ConfiguredProduct(menuItemId = 1L, name = "Simple Item", quantity = 1,
                baseUnitPrice = BigDecimal("100.00"), unitTotal = BigDecimal("100.00"), total = BigDecimal("100.00")),
            rewardProducts = listOf(wrongReward)
        )
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertTrue("SAME_ITEM must reject reward with different menuItemId", cart.isEmpty())
    }
}

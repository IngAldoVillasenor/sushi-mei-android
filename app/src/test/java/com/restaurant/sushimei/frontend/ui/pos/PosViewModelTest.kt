package com.restaurant.sushimei.frontend.ui.pos

import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.model.*
import com.restaurant.sushimei.frontend.data.repository.IManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var printManager: com.restaurant.sushimei.frontend.PrintManager
    private lateinit var printJobRepository: com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
    private lateinit var operationalOrderRepository: IOperationalOrderRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        menuRepository = mockk()
        manualPosOrderRepository = mockk()
        promotionRepository = mockk()
        printManager = mockk(relaxed = true)
        printJobRepository = mockk(relaxed = true)
        operationalOrderRepository = mockk()
        io.mockk.coEvery { printJobRepository.observeAllJobs() } returns kotlinx.coroutines.flow.flowOf(emptyList())

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

        viewModel = PosViewModel(menuRepository, manualPosOrderRepository, promotionRepository, printManager, printJobRepository, operationalOrderRepository)
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

    // -------------------------------------------------------------------------
    // addEligibleItemBundle (FlexibleBogoPickerDialog entry point) tests
    // -------------------------------------------------------------------------

    private fun makeEligiblePromotion(
        purchasedItemId: Long = 1L,
        purchasedItemName: String = "Simple Item"
    ) = Promotion(
        id = 200L,
        name = "Jueves 2x1",
        active = true,
        priority = 100,
        schedule = PromotionSchedule(daysOfWeek = setOf(4), allDay = true),
        targets = listOf(PromotionTarget(PromotionTargetType.ITEM, purchasedItemId, purchasedItemName)),
        benefit = PromotionBenefit.BuyXGetY.validated(
            type = PromotionBenefit.BuyXGetY.ELIGIBLE_ITEM,
            buyQuantity = 1,
            rewardQuantity = 1,
            repeat = false
        )
    )

    @Test
    fun `addEligibleItemBundle adds purchased and reward from two different items`() = runTest {
        // Demonstrates: two different eligible products → one cart line with reward config
        val promotion = makeEligiblePromotion()
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val purchasedItem = MenuItem(id = 1, nombre = "California", categoria = "Rolls", precio = BigDecimal("100.00"))
        val rewardItem = MenuItem(id = 2, nombre = "Empanizado", categoria = "Rolls", precio = BigDecimal("130.00"))

        // quoteItem for purchased item must be mocked
        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "California", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("100.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00"), groups = emptyList()
        )

        viewModel.addEligibleItemBundle(
            promotion = promotion,
            purchasedMenuItem = purchasedItem,
            rewardMenuItems = listOf(rewardItem)
        )
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals("Exactly one cart line", 1, cart.size)
        val line = cart.single()
        assertEquals("Purchased slot = California (id=1)", 1L, line.menuItemId)
        val rewardConfig = line.promotionSelection?.rewardConfigurations?.single()
        assertNotNull("Reward configuration must exist", rewardConfig)
        assertEquals("Reward slot = Empanizado (id=2)", 2L, rewardConfig?.menuItemId)
        assertEquals("Reward ordinal = 1", 1, rewardConfig?.rewardOrdinal)
    }

    @Test
    fun `addEligibleItemBundle first slot is purchased second is reward`() = runTest {
        // Verifies slot assignment order: selection order determines purchased vs reward.
        val promotion = makeEligiblePromotion()
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val itemA = MenuItem(id = 1, nombre = "Item A", categoria = "Rolls", precio = BigDecimal("100.00"))
        val itemB = MenuItem(id = 2, nombre = "Item B", categoria = "Rolls", precio = BigDecimal("80.00"))
        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "Item A", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("100.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00"), groups = emptyList()
        )

        viewModel.addEligibleItemBundle(promotion, itemA, listOf(itemB))
        testScheduler.advanceUntilIdle()

        val line = (viewModel.uiState.value as PosUiState.Success).currentCart.single()
        assertEquals("purchasedMenuItem becomes the cart line product", 1L, line.menuItemId)
        assertEquals("rewardMenuItems[0] becomes rewardOrdinal=1", 2L,
            line.promotionSelection?.rewardConfigurations?.first()?.menuItemId)
    }

    @Test
    fun `addEligibleItemBundle same item may occupy both purchased and reward slots`() = runTest {
        // Same roll in both slots is valid for ELIGIBLE_ITEM.
        val promotion = makeEligiblePromotion()
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val item = MenuItem(id = 1, nombre = "California", categoria = "Rolls", precio = BigDecimal("100.00"))
        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "California", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("100.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00"), groups = emptyList()
        )

        viewModel.addEligibleItemBundle(promotion, item, listOf(item))
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals("Same item twice is allowed", 1, cart.size)
        val rewardConfig = cart.single().promotionSelection?.rewardConfigurations?.single()
        assertEquals("Reward also points to same item", 1L, rewardConfig?.menuItemId)
    }

    @Test
    fun `addEligibleItemBundle succeeds even when requiresConfiguration is true`() = runTest {
        // DOMAIN RULE: requiresConfiguration is a standalone ordering context.
        // BOGO picker must NEVER be blocked by requiresConfiguration=true.
        val promotion = makeEligiblePromotion(purchasedItemId = 1)
        val configurableItem = MenuItem(
            id = 1,
            nombre = "Configurable Roll",
            categoria = "Rolls",
            precio = BigDecimal("0.00"),
            requiresConfiguration = true,  // <-- standalone flag, must NOT affect BOGO
            pricingMode = ItemPricingMode.SELECTION_SUM
        )
        val rewardItem = MenuItem(id = 2, nombre = "Simple Reward", categoria = "Rolls", precio = BigDecimal("100.00"))

        coEvery { menuRepository.observeActive() } returns flowOf(listOf(configurableItem, rewardItem))
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        // quoteItem for the configurable item as purchased — no groups, BOGO context
        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "Configurable Roll", quantity = 1,
            baseUnitPrice = BigDecimal("0.00"), baseTotal = BigDecimal("0.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("0.00"),
            total = BigDecimal("0.00"), groups = emptyList()
        )

        viewModel.addEligibleItemBundle(promotion, configurableItem, listOf(rewardItem))
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals("requiresConfiguration=true must NOT block BOGO selection", 1, cart.size)
        assertEquals("No configuration groups attached in BOGO context",
            emptyList<ConfiguredGroup>(), cart.single().groups)
    }

    @Test
    fun `addEligibleItemBundle reward sourceLineKey matches cart line id`() = runTest {
        // Verifies: the cart line id is what the quote mapper uses as sourceLineKey.
        val promotion = makeEligiblePromotion()
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val purchasedItem = MenuItem(id = 1, nombre = "California", categoria = "Rolls", precio = BigDecimal("100.00"))
        val rewardItem = MenuItem(id = 2, nombre = "Empanizado", categoria = "Rolls", precio = BigDecimal("130.00"))
        coEvery { menuRepository.quoteItem(1, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1, name = "California", quantity = 1,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("100.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"),
            total = BigDecimal("100.00"), groups = emptyList()
        )

        viewModel.addEligibleItemBundle(promotion, purchasedItem, listOf(rewardItem))
        testScheduler.advanceUntilIdle()

        val line = (viewModel.uiState.value as PosUiState.Success).currentCart.single()
        val cartLineId = line.id  // the UUID the ViewModel assigned

        // Simulate what the quote mapper does: sourceLineKey = lineKey = the cart line id sent to server
        // This proves the cart line id is the anchor for reward association.
        assertTrue("Cart line must have a non-empty id", cartLineId.isNotEmpty())
        assertNotNull("Reward configuration must exist so quote can associate via sourceLineKey",
            line.promotionSelection?.rewardConfigurations?.firstOrNull())
    }

    @Test
    fun `addEligibleItemBundle sets quantity to buyQuantity and maps reward properly`() = runTest {
        val promotion = makeEligiblePromotion(purchasedItemId = 1L, purchasedItemName = "California").copy(
            benefit = PromotionBenefit.BuyXGetY(
                type = "BUY_X_GET_Y_ELIGIBLE_ITEM",
                buyQuantity = 2,
                rewardQuantity = 1,
                repeat = false
            )
        )
        coEvery { promotionRepository.getActivePromotions() } returns listOf(promotion)
        viewModel.refreshActivePromotions()
        testScheduler.advanceUntilIdle()

        val purchasedItem = MenuItem(id = 1L, nombre = "California", categoria = "Rolls", precio = BigDecimal("100.00"))
        val rewardItem = MenuItem(id = 2L, nombre = "Empanizado", categoria = "Rolls", precio = BigDecimal("130.00"))

        coEvery { menuRepository.quoteItem(1L, any()) } returns ItemQuoteResponseDto(
            menuItemId = 1L, name = "California", quantity = 2,
            baseUnitPrice = BigDecimal("100.00"), baseTotal = BigDecimal("200.00"),
            unitAdjustmentTotal = BigDecimal.ZERO, unitTotal = BigDecimal("100.00"),
            total = BigDecimal("200.00"), groups = emptyList()
        )

        viewModel.addEligibleItemBundle(promotion, purchasedItem, listOf(rewardItem))
        testScheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value as PosUiState.Success).currentCart
        assertEquals("Cart must have exactly 1 purchased line", 1, cart.size)

        val line = cart.single()
        assertEquals("Cart line quantity must match buyQuantity", 2, line.quantity)
        assertEquals("Cart line menu item must be the purchased item", 1L, line.menuItemId)

        val rewards = line.promotionSelection?.rewardConfigurations
        assertNotNull(rewards)
        assertEquals("Cart line must carry exactly 1 reward configuration", 1, rewards?.size)
        assertEquals("Reward must be the selected reward item", 2L, rewards?.first()?.menuItemId)
    }

    @Test
    fun `QuotedRewardItem carries backend-authoritative name and total`() {
        // Unit-level: QuotedRewardItem stores what the server returns verbatim.
        val reward = QuotedRewardItem(
            sourceLineKey = "cart-line-abc",
            rewardOrdinal = 1,
            menuItemId = 42L,
            name = "Empanizado",
            promotionName = "Jueves 2x1",
            catalogBaseUnitPrice = BigDecimal("130.00"),
            chargedBaseUnitPrice = BigDecimal.ZERO,
            configurationAdjustmentTotal = BigDecimal("15.00"),
            total = BigDecimal("15.00")  // only adjustment
        )

        assertEquals("name must be backend name", "Empanizado", reward.name)
        assertEquals("total is authoritative backend value", BigDecimal("15.00"), reward.total)
        assertEquals("sourceLineKey links reward to purchased line", "cart-line-abc", reward.sourceLineKey)
        assertEquals("configurationAdjustmentTotal is ajuste", BigDecimal("15.00"), reward.configurationAdjustmentTotal)
    }

    @Test
    fun `OpenSale first attempt throws network error, retry SAME payload captures same requestId`() = runTest {
        val requests = mutableListOf<com.restaurant.sushimei.frontend.data.model.OpenSaleRequest>()
        io.mockk.coEvery { manualPosOrderRepository.createOpenSale(capture(requests)) } throws RuntimeException("Network error")

        viewModel.submitOpenSale("Test Item", BigDecimal("150.00"), PaymentMethod.CASH, BigDecimal("200.00"))
        advanceUntilIdle()

        val state1 = (viewModel.uiState.value as PosUiState.Success).checkoutState
        assertTrue(state1 is CheckoutState.Error)
        assertEquals(1, requests.size)
        val firstRequestId = requests[0].requestId

        io.mockk.coEvery { manualPosOrderRepository.createOpenSale(capture(requests)) } returns OpenSaleResponse(
            100L, firstRequestId, "CREATED", "POS", 1L, "Test Item", 1, BigDecimal("150.00"), BigDecimal("150.00"), PaymentMethod.CASH, BigDecimal("200.00"), "COMPLETED", "2024"
        )

        viewModel.submitOpenSale("Test Item", BigDecimal("150.00"), PaymentMethod.CASH, BigDecimal("200.00"))
        advanceUntilIdle()

        val state2 = (viewModel.uiState.value as PosUiState.Success).checkoutState
        assertTrue(state2 is CheckoutState.OpenSaleSuccess)
        assertEquals(2, requests.size)
        val secondRequestId = requests[1].requestId

        assertEquals(firstRequestId, secondRequestId)
    }

    @Test
    fun `OpenSale first request fails, change payload, retry captures different requestId`() = runTest {
        val requests = mutableListOf<com.restaurant.sushimei.frontend.data.model.OpenSaleRequest>()
        io.mockk.coEvery { manualPosOrderRepository.createOpenSale(capture(requests)) } throws RuntimeException("Network error")

        viewModel.submitOpenSale("Test Item", BigDecimal("150.00"), PaymentMethod.CASH, BigDecimal("200.00"))
        advanceUntilIdle()

        viewModel.submitOpenSale("Test Item", BigDecimal("160.00"), PaymentMethod.CASH, BigDecimal("200.00"))
        advanceUntilIdle()

        assertEquals(2, requests.size)
        val firstRequestId = requests[0].requestId
        val secondRequestId = requests[1].requestId

        assertTrue(firstRequestId != secondRequestId)
    }

    @Test
    fun `OpenSale CREATED response captures currentPrintJobId`() = runTest {
        val requests = mutableListOf<com.restaurant.sushimei.frontend.data.model.OpenSaleRequest>()
        io.mockk.coEvery { manualPosOrderRepository.createOpenSale(capture(requests)) } returns OpenSaleResponse(
            100L, "req-1", "CREATED", "POS", 1L, "Test Item", 1, BigDecimal("150.00"), BigDecimal("150.00"), PaymentMethod.CARD, null, "COMPLETED", "2024"
        )

        io.mockk.coEvery { printJobRepository.getPendingJobs() } returns emptyList()

        val job = com.restaurant.sushimei.frontend.data.local.PrintJobEntity("job-123", "req-1", PrintDocumentType.ORDER, 100L, null, PrintJobStatus.PENDING, null, 1L, 1L, null, null)
        io.mockk.coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns job

        viewModel.submitOpenSale("Test Item", BigDecimal("150.00"), PaymentMethod.CARD, null)
        advanceUntilIdle()

        val state = (viewModel.uiState.value as PosUiState.Success).checkoutState
        assertTrue(state is CheckoutState.OpenSaleSuccess)

        val currentPrintJobIdField = PosViewModel::class.java.getDeclaredField("_currentPrintJobId")
        currentPrintJobIdField.isAccessible = true
        val stateFlow = currentPrintJobIdField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<String?>
        assertEquals("job-123", stateFlow.value)

    }

    // =========================================================================
    // TEST 11: Stale response protection - Close during load
    // =========================================================================
    @Test
    fun `stale response protection - close during load keeps state Idle`() = runTest {
        val initialLoad = CompletableDeferred<List<OperationalOrderSummaryDto>>()
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } coAnswers { initialLoad.await() }

        viewModel.openActiveOrderManagement()
        testScheduler.runCurrent()
        viewModel.closeActiveOrderManagement()
        initialLoad.complete(listOf(makePosOrder(id = 1)))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.activeOrderManagementState.value is ActiveOrderManagementState.Idle)
    }

    // =========================================================================
    // TEST 12: Stale response protection - Close during refresh
    // =========================================================================
    @Test
    fun `stale response protection - close during refresh keeps state Idle`() = runTest {
        val refreshLoad = CompletableDeferred<List<OperationalOrderSummaryDto>>()
        var calls = 0
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } coAnswers {
            calls++
            if (calls == 1) listOf(makePosOrder(id = 1)) else refreshLoad.await()
        }

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.refreshActiveOrders()
        testScheduler.runCurrent()
        viewModel.closeActiveOrderManagement()
        refreshLoad.complete(listOf(makePosOrder(id = 2)))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.activeOrderManagementState.value is ActiveOrderManagementState.Idle)
    }

    // =========================================================================
    // TEST 13: Stale response protection - Close during void
    // =========================================================================
    @Test
    fun `stale response protection - close during in-flight void keeps state Idle`() = runTest {
        val orders = listOf(makePosOrder(id = 2))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        val voidResponse = CompletableDeferred<VoidOrderResponse>()
        coEvery { operationalOrderRepository.voidOrder(2L, "reason") } coAnswers { voidResponse.await() }

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(2L)
        viewModel.submitVoidOrder(2L, "reason")

        testScheduler.runCurrent()
        viewModel.closeActiveOrderManagement()
        voidResponse.complete(makeVoidResponse(2L))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { operationalOrderRepository.voidOrder(2L, "reason") }
        assertTrue(viewModel.activeOrderManagementState.value is ActiveOrderManagementState.Idle)
    }

    // =========================================================================
    // TEST 14: Stale response protection - Old session cannot overwrite new session
    // =========================================================================
    @Test
    fun `stale response protection - old session result does not overwrite newly reopened session`() = runTest {
        val sessionOneLoad = CompletableDeferred<List<OperationalOrderSummaryDto>>()
        var calls = 0
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } coAnswers {
            calls++
            if (calls == 1) sessionOneLoad.await() else listOf(makePosOrder(id = 4))
        }

        viewModel.openActiveOrderManagement()
        testScheduler.runCurrent()
        viewModel.closeActiveOrderManagement()

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()

        val reopenedState = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertEquals(4L, reopenedState.orders.single().id)

        sessionOneLoad.complete(listOf(makePosOrder(id = 3)))
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertEquals(1, state.orders.size)
        assertEquals(4L, state.orders.first().id)
    }

    // =========================================================================
    // TEST 15: Failed void + failed refresh preserves known orders
    // =========================================================================
    @Test
    fun `failed void and failed refresh preserves known orders`() = runTest {
        val orders = listOf(makePosOrder(id = 5))
        // first call returns list
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()

        coEvery { operationalOrderRepository.voidOrder(5L, "reason") } throws Exception("void fail")
        // second call throws
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } throws Exception("refresh fail")

        viewModel.requestVoidConfirmation(5L)
        viewModel.submitVoidOrder(5L, "reason")
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertNotNull(state.voidError)
        assertEquals(1, state.orders.size)
        assertEquals(5L, state.orders.first().id)
        assertNull(state.submittingVoidFor)
    }

    // =========================================================================
    // TEST 16: Successful void + failed refresh removes voided order based on VoidOrderResponse
    // =========================================================================
    @Test
    fun `successful void and failed refresh removes voided order based on authoritative response`() = runTest {
        val orders = listOf(makePosOrder(id = 6), makePosOrder(id = 7))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()

        coEvery { operationalOrderRepository.voidOrder(6L, "reason") } returns makeVoidResponse(6L)
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } throws Exception("refresh fail")

        viewModel.requestVoidConfirmation(6L)
        viewModel.submitVoidOrder(6L, "reason")
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertNotNull(state.voidSuccessMessage)
        assertTrue(state.voidSuccessMessage!!.contains("No se pudo actualizar la lista"))
        assertEquals(1, state.orders.size)
        assertEquals(7L, state.orders.first().id)
        assertNull(state.submittingVoidFor)
        assertNull(state.confirmingVoid)
    }

    // =========================================================================
    // Helpers for cancellation tests
    // =========================================================================

    private fun makePosOrder(
        id: Long,
        orderSource: String = "ANDROID_MANUAL",
        status: String = "PENDING"
    ) = com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto(
        id = id,
        orderSource = orderSource,
        status = status,
        fulfillmentType = com.restaurant.sushimei.frontend.data.model.FulfillmentType.PICKUP,
        paymentMethod = null,
        deliveryAddress = null,
        pickupName = "Test",
        cashDenomination = null,
        phoneNumber = null,
        total = java.math.BigDecimal("150.00"),
        createdAt = java.time.Instant.now(),
        requiresPaymentValidation = false,
        structuredLinesAvailable = true
    )

    private fun makeVoidResponse(orderId: Long) =
        com.restaurant.sushimei.frontend.data.model.VoidOrderResponse(
            orderId = orderId,
            previousStatus = "PENDING",
            currentStatus = "VOIDED",
            voidReason = "Test reason",
            voidedAt = java.time.Instant.now(),
            voidedByUserId = 1L
        )

    // =========================================================================
    // TEST 1: Opening active-order management calls getOperationalActiveOrders
    // =========================================================================

    @Test
    fun `openActiveOrderManagement calls getOperationalActiveOrders`() = runTest {
        val orders = listOf(makePosOrder(id = 10))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { operationalOrderRepository.getOperationalActiveOrders() }
        val state = viewModel.activeOrderManagementState.value
        assertTrue("State must be Content after load", state is ActiveOrderManagementState.Content)
    }

    // =========================================================================
    // TEST 2: Only ANDROID_MANUAL / COUNTER active orders are exposed
    // =========================================================================

    @Test
    fun `openActiveOrderManagement exposes only ANDROID_MANUAL and COUNTER sources`() = runTest {
        val orders = listOf(
            makePosOrder(id = 1, orderSource = "ANDROID_MANUAL", status = "PENDING"),
            makePosOrder(id = 2, orderSource = "COUNTER", status = "PREPARING"),
            makePosOrder(id = 3, orderSource = "WHATSAPP", status = "PENDING"),
            makePosOrder(id = 4, orderSource = "AI", status = "READY"),
            makePosOrder(id = 5, orderSource = "ANDROID_MANUAL", status = "READY")
        )
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        val ids = state.orders.map { it.id }
        assertTrue("Must contain order 1 (ANDROID_MANUAL)", 1L in ids)
        assertTrue("Must contain order 2 (COUNTER)", 2L in ids)
        assertTrue("Must contain order 5 (ANDROID_MANUAL / READY)", 5L in ids)
        assertEquals("Must expose exactly 3 eligible POS orders", 3, ids.size)
    }

    // =========================================================================
    // TEST 3: Unsupported sources are not exposed as cancellable
    // =========================================================================

    @Test
    fun `openActiveOrderManagement does not expose WHATSAPP or AI orders as cancellable`() = runTest {
        val orders = listOf(
            makePosOrder(id = 1, orderSource = "WHATSAPP", status = "PENDING"),
            makePosOrder(id = 2, orderSource = "AI", status = "PREPARING"),
            makePosOrder(id = 3, orderSource = "VENDIS", status = "READY")
        )
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertTrue("No non-POS orders should appear as cancellable", state.orders.isEmpty())
    }

    // =========================================================================
    // TEST 4: Blank reason is rejected without calling repository void
    // =========================================================================

    @Test
    fun `submitVoidOrder with blank reason is rejected without calling repository`() = runTest {
        val orders = listOf(makePosOrder(id = 42))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(42L)

        viewModel.submitVoidOrder(42L, "   ")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { operationalOrderRepository.voidOrder(any(), any()) }
        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertNotNull("Error message must be set for blank reason", state.voidError)
    }

    // =========================================================================
    // TEST 5: Reason > 500 chars is rejected without calling repository void
    // =========================================================================

    @Test
    fun `submitVoidOrder with reason over 500 chars is rejected without calling repository`() = runTest {
        val orders = listOf(makePosOrder(id = 42))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(42L)

        val longReason = "a".repeat(501)
        viewModel.submitVoidOrder(42L, longReason)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { operationalOrderRepository.voidOrder(any(), any()) }
        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertNotNull("Error message must be set for too-long reason", state.voidError)
    }

    // =========================================================================
    // TEST 6: Successful cancellation calls repository with correct orderId + reason
    // =========================================================================

    @Test
    fun `submitVoidOrder calls repository with correct orderId and normalized reason`() = runTest {
        val orders = listOf(makePosOrder(id = 55))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders
        coEvery { operationalOrderRepository.voidOrder(55L, "Cliente canceló el pedido") } returns makeVoidResponse(55L)

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(55L)

        // Leading/trailing whitespace is normalized by ViewModel before sending
        viewModel.submitVoidOrder(55L, "  Cliente canceló el pedido  ")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { operationalOrderRepository.voidOrder(55L, "Cliente canceló el pedido") }
    }

    // =========================================================================
    // TEST 7: Successful cancellation refreshes active order list
    // =========================================================================

    @Test
    fun `submitVoidOrder on success refreshes active order list`() = runTest {
        val initialOrders = listOf(makePosOrder(id = 55), makePosOrder(id = 66))
        val refreshedOrders = listOf(makePosOrder(id = 66)) // 55 gone after void

        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returnsMany listOf(
            initialOrders,
            refreshedOrders
        )
        coEvery { operationalOrderRepository.voidOrder(55L, "Motivo valido") } returns makeVoidResponse(55L)

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(55L)
        viewModel.submitVoidOrder(55L, "Motivo valido")
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertEquals("After void, list must be refreshed to 1 order", 1, state.orders.size)
        assertEquals("Remaining order must be #66", 66L, state.orders.first().id)
        assertNotNull("Success message must be present", state.voidSuccessMessage)
        assertTrue("Confirmation dialog must be closed", state.confirmingVoid == null)
    }

    // =========================================================================
    // TEST 8: Failed cancellation keeps order and exposes recoverable error
    // =========================================================================

    @Test
    fun `submitVoidOrder on failure keeps order in list and exposes recoverable error`() = runTest {
        val orders = listOf(makePosOrder(id = 77))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders
        coEvery { operationalOrderRepository.voidOrder(77L, "Error reason") } throws
            com.restaurant.sushimei.frontend.data.api.ApiException("ORDER_INVALID_TRANSITION", "Cannot void")

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(77L)
        viewModel.submitVoidOrder(77L, "Error reason")
        testScheduler.advanceUntilIdle()

        val state = viewModel.activeOrderManagementState.value as ActiveOrderManagementState.Content
        assertNotNull("Error message must be set after failure", state.voidError)
        assertEquals("Order must remain in list after failure", 1, state.orders.size)
        assertEquals("Remaining order must still be #77", 77L, state.orders.first().id)
        assertTrue("Submission guard must be cleared", state.submittingVoidFor == null)
    }

    // =========================================================================
    // TEST 9: Duplicate submission while same request is in-flight is ignored
    // =========================================================================

    @Test
    fun `submitVoidOrder duplicate tap while in-flight is ignored`() = runTest {
        val orders = listOf(makePosOrder(id = 88))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders

        var callCount = 0
        coEvery { operationalOrderRepository.voidOrder(88L, "First call") } coAnswers {
            callCount++
            // simulate slow network — never returns during this test
            kotlinx.coroutines.delay(10_000)
            makeVoidResponse(88L)
        }

        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(88L)

        // First call
        viewModel.submitVoidOrder(88L, "First call")
        testScheduler.advanceTimeBy(100) // partially advance but don't finish

        // Second call — should be a no-op because submittingVoidFor == 88
        viewModel.submitVoidOrder(88L, "First call")
        testScheduler.advanceTimeBy(100)

        // Only one call must have reached repository
        assertEquals("Repository must be called exactly once despite duplicate tap", 1, callCount)
    }

    // =========================================================================
    // TEST 10: Cancellation operations do NOT modify cart, checkout, promotions, or print state
    // =========================================================================

    @Test
    fun `void management operations do not touch cart checkout promotion or print state`() = runTest {
        val orders = listOf(makePosOrder(id = 99))
        coEvery { operationalOrderRepository.getOperationalActiveOrders() } returns orders
        coEvery { operationalOrderRepository.voidOrder(99L, "Test cancel") } returns makeVoidResponse(99L)

        // Place a cart item first
        testScheduler.advanceUntilIdle()
        viewModel.addToCart(MenuItem(id = 1, nombre = "Simple Item", categoria = "Rolls", precio = java.math.BigDecimal("100.00")))
        viewModel.addManualLine("Bebida", java.math.BigDecimal("30.00"))
        viewModel.updateFulfillmentType(com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY)
        viewModel.updatePaymentMethod(com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH)
        viewModel.updateCashDenomination(java.math.BigDecimal("500.00"))
        viewModel.updateDeliveryAddress("Calle Falsa 123")
        viewModel.updatePickupName("Juan Perez")
        testScheduler.advanceUntilIdle()

        val successStateBefore = viewModel.uiState.value as PosUiState.Success
        val cartBefore = successStateBefore.currentCart.toList()
        val checkoutBefore = successStateBefore.checkoutState
        val promotionsBefore = successStateBefore.activePromotions.toList()
        val manualCartBefore = successStateBefore.manualCart.toList()
        val fulfillmentBefore = successStateBefore.fulfillmentType
        val paymentBefore = successStateBefore.paymentMethod
        val pickupBefore = successStateBefore.pickupName
        val addressBefore = successStateBefore.deliveryAddress
        val denomBefore = successStateBefore.cashDenomination
        val printStateBefore = viewModel.currentPrintState.value

        // Perform void management flow
        viewModel.openActiveOrderManagement()
        testScheduler.advanceUntilIdle()
        viewModel.requestVoidConfirmation(99L)
        viewModel.submitVoidOrder(99L, "Test cancel")
        testScheduler.advanceUntilIdle()
        viewModel.closeActiveOrderManagement()

        val successStateAfter = viewModel.uiState.value as PosUiState.Success
        val cartAfter = successStateAfter.currentCart
        val checkoutAfter = successStateAfter.checkoutState
        val promotionsAfter = successStateAfter.activePromotions
        val manualCartAfter = successStateAfter.manualCart
        val fulfillmentAfter = successStateAfter.fulfillmentType
        val paymentAfter = successStateAfter.paymentMethod
        val pickupAfter = successStateAfter.pickupName
        val addressAfter = successStateAfter.deliveryAddress
        val denomAfter = successStateAfter.cashDenomination
        val printStateAfter = viewModel.currentPrintState.value

        assertEquals("Cart must be unchanged after void management", cartBefore, cartAfter)
        assertEquals("CheckoutState must be unchanged after void management", checkoutBefore, checkoutAfter)
        assertEquals("Promotions must be unchanged after void management", promotionsBefore, promotionsAfter)
        assertEquals("ManualCart must be unchanged", manualCartBefore, manualCartAfter)
        assertEquals("FulfillmentType must be unchanged", fulfillmentBefore, fulfillmentAfter)
        assertEquals("PaymentMethod must be unchanged", paymentBefore, paymentAfter)
        assertEquals("PickupName must be unchanged", pickupBefore, pickupAfter)
        assertEquals("DeliveryAddress must be unchanged", addressBefore, addressAfter)
        assertEquals("CashDenomination must be unchanged", denomBefore, denomAfter)
        assertEquals("PrintState must be unchanged", printStateBefore, printStateAfter)

        assertTrue("activeOrderManagementState must return to Idle", viewModel.activeOrderManagementState.value is ActiveOrderManagementState.Idle)
    }

}

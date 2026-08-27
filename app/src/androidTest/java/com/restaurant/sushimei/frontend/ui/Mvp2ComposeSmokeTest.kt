package com.restaurant.sushimei.frontend.ui


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.ItemPricingMode
import com.restaurant.sushimei.frontend.ui.screens.MenuItemCard
import com.restaurant.sushimei.frontend.ui.screens.ManualSaleDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

@RunWith(AndroidJUnit4::class)
class Mvp2ComposeSmokeTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<com.restaurant.sushimei.frontend.TestHostActivity>()

    @Test
    fun testNormalTapAndLongPress() {
        var normalClicked = false
        var longClicked = false

        val item = MenuItem(
            id = 1L,
            nombre = "Ramen",
            categoria = "Sopas",
            precio = BigDecimal.TEN,
            descripcion = "Delicioso",
            emoji = "🍜",
            activo = true,
            standaloneOrderable = true,
            requiresConfiguration = false,
            pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            tags = emptyList()
        )

        composeTestRule.setContent {
            MenuItemCard(
                menuItem = item,
                cartQuantity = 0,
                onAddToCart = { normalClicked = true },
                onLongPress = { longClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Ramen").performClick()
        assertTrue(normalClicked)
        assertFalse(longClicked)

        normalClicked = false
        composeTestRule.onNodeWithText("Ramen").performTouchInput { longClick() }
        assertFalse(normalClicked)
        assertTrue(longClicked)
    }

    @Test
    fun testManualSaleEntryPointFlow() {
        var submittedDesc: String? = null
        var submittedAmt: java.math.BigDecimal? = null

        composeTestRule.setContent {
            com.restaurant.sushimei.frontend.ui.screens.ManualSaleEntryPoint(
                onSubmit = { desc, amt ->
                    submittedDesc = desc
                    submittedAmt = amt
                }
            )
        }

        // 1. Button should be visible
        composeTestRule.onNodeWithText("Registrar venta libre").assertIsDisplayed()

        // Click button to show dialog
        composeTestRule.onNodeWithText("Registrar venta libre").performClick()

        // Dialog fields:
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Descripcin").or(androidx.compose.ui.test.hasText("Descripci\u00F3n")).or(androidx.compose.ui.test.hasText("Descripcin")).or(androidx.compose.ui.test.hasText("Descripción"))).assertIsDisplayed()
        composeTestRule.onNodeWithText("Monto").assertIsDisplayed()

        // Ensure legacy elements do NOT exist
        composeTestRule.onNodeWithText("Venta Libre").assertDoesNotExist()
        composeTestRule.onNodeWithText("Efectivo Recibido").assertDoesNotExist()
        composeTestRule.onNodeWithText("Registrar").assertDoesNotExist()

        // 2. Fill values
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Descripcin").or(androidx.compose.ui.test.hasText("Descripci\u00F3n")).or(androidx.compose.ui.test.hasText("Descripcin")).or(androidx.compose.ui.test.hasText("Descripción"))).performTextInput("Misc item")
        composeTestRule.onNodeWithText("Monto").performTextInput("150.50")

        // 3. Submit
        composeTestRule.onNodeWithText("Agregar al carrito").performClick()
        composeTestRule.waitForIdle()

        // Assert callback
        org.junit.Assert.assertEquals("Misc item", submittedDesc)
        org.junit.Assert.assertEquals(java.math.BigDecimal("150.50"), submittedAmt)

        // Assert dialog closes (the form fields will disappear)
        composeTestRule.onNodeWithText("Monto").assertDoesNotExist()
    }

    @Test
    fun testConfiguratorRemovableComponentsFiltering() {
        val arroz = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(1, "ARR", "Arroz", null, true, false, 1, true)
        val alga = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(2, "ALG", "Alga", null, true, true, 2, true)
        val pepino = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(3, "PEP", "Pepino", null, true, true, 3, true)

        val testConfig = com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto(
            menuItemId = 1L, name = "Generic Bento", standaloneOrderable = true, basePrice = java.math.BigDecimal.TEN, requiresConfiguration = false, groups = emptyList()
        )

        val repo = object : com.restaurant.sushimei.frontend.data.repository.IMenuRepository {
            override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.restaurant.sushimei.frontend.data.model.MenuItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override fun observeActive(): kotlinx.coroutines.flow.Flow<List<com.restaurant.sushimei.frontend.data.model.MenuItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override fun observeActiveCategories(): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun refreshCatalog(standaloneOnly: Boolean?) {}
            override suspend fun getCategories(): List<String> = emptyList()
            override suspend fun getProducts(): List<com.restaurant.sushimei.frontend.data.model.MenuItem> = emptyList()
            override suspend fun createProduct(request: com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse = TODO()
            override suspend fun updateProduct(id: Long, request: com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse = TODO()
            override suspend fun deleteProduct(id: Long) {}
            override suspend fun setActive(id: Long, activo: Boolean) {}
            override suspend fun getTags(): List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto> = emptyList()
            override suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.TagCreateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto = TODO()
            override suspend fun updateTag(id: Long, tag: com.restaurant.sushimei.frontend.data.model.TagUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto = TODO()
            override suspend fun deleteTag(id: Long) {}
            override suspend fun getConfiguration(menuItemId: Long) = testConfig
            override suspend fun getMenuItemComponents(menuItemId: Long) = listOf(arroz, alga, pepino)
            override suspend fun quoteItem(menuItemId: Long, request: com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto): com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto = com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto(1L, "Ramen", 1, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN, emptyList(), java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN)
        }

        val vm = com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorViewModel(repo)
        vm.loadConfiguration(1L)

        composeTestRule.setContent {
            com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorScreen(
                menuItemId = 1L,
                viewModel = vm,
                onDismiss = { },
                onAddToCart = { }
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Alga", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pepino", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Arroz", substring = true).assertCountEquals(0)
    }


    @Test
    fun testKitchenOperationalLineRendering() {
        val detail = com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto(
            id = 100,
            requestId = "req-1",
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY,
            paymentMethod = com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH,
            deliveryAddress = "Av. Siempre Viva 742",
            pickupName = null,
            cashDenomination = java.math.BigDecimal("500.00"),
            phoneNumber = "555-1234",
            status = "PENDING",
            createdAt = java.time.Instant.now(),
            total = java.math.BigDecimal("350.00"),
            paymentNotes = null,
            transferReceiptPath = null,
            legacyOrderDetails = null,
            lines = listOf(
                com.restaurant.sushimei.frontend.data.model.OperationalOrderLineDto(
                    id = 1,
                    lineKind = "ITEM",
                    lineKey = "key1",
                    sourceMenuItemId = 1L,
                    name = "California roll",
                    quantity = 1,
                    catalogBaseUnitPrice = java.math.BigDecimal("79.00"),
                    chargedBaseUnitPrice = java.math.BigDecimal("79.00"),
                    configurationAdjustmentAmount = java.math.BigDecimal.ZERO,
                    finalUnitAmount = java.math.BigDecimal("79.00"),
                    finalLineTotal = java.math.BigDecimal("79.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    omittedComponents = listOf(
                        com.restaurant.sushimei.frontend.data.model.OrderComponentOmissionSnapshotDto(
                            id = 1,
                            sourceComponentId = 2,
                            code = "ALG",
                            displayName = "Alga",
                            detail = "Por fuera",
                            displayOrder = 1
                        ),
                        com.restaurant.sushimei.frontend.data.model.OrderComponentOmissionSnapshotDto(
                            id = 2,
                            sourceComponentId = 3,
                            code = "SUR",
                            displayName = "Surimi",
                            detail = null,
                            displayOrder = 2
                        )
                    ),
                    note = "Poca salsa"
                ),
                com.restaurant.sushimei.frontend.data.model.OperationalOrderLineDto(
                    id = 2,
                    lineKind = "ITEM",
                    lineKey = "key2",
                    sourceMenuItemId = 2L,
                    name = "Maki",
                    quantity = 1,
                    catalogBaseUnitPrice = java.math.BigDecimal("50.00"),
                    chargedBaseUnitPrice = java.math.BigDecimal("50.00"),
                    configurationAdjustmentAmount = java.math.BigDecimal.ZERO,
                    finalUnitAmount = java.math.BigDecimal("50.00"),
                    finalLineTotal = java.math.BigDecimal("50.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    omittedComponents = emptyList(),
                    note = null
                )
            )
        )

        val summary = com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto(
            id = 100,
            status = "PENDING",
            fulfillmentType = com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY,
            paymentMethod = com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH,
            cashDenomination = java.math.BigDecimal("500.00"),
            deliveryAddress = "Av. Siempre Viva 742",
            pickupName = null,
            createdAt = java.time.Instant.now(),
            total = java.math.BigDecimal("350.00"),
            orderSource = "POS",
            phoneNumber = null,
            requiresPaymentValidation = false,
            structuredLinesAvailable = true
        )

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dummyRepo = object : com.restaurant.sushimei.frontend.data.repository.IOrderRepository {
            override val activeOrders: kotlinx.coroutines.flow.StateFlow<List<com.restaurant.sushimei.frontend.data.model.Order>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
            override suspend fun placeOrder(items: List<com.restaurant.sushimei.frontend.data.model.ConfiguredProduct>, total: java.math.BigDecimal) {}
            override suspend fun acceptOrder(orderId: Long) {}
            override suspend fun markReady(orderId: Long) {}
            override suspend fun dispatch(orderId: Long) {}
            override fun observeDispatchedToday() = kotlinx.coroutines.flow.flowOf(emptyList<com.restaurant.sushimei.frontend.data.model.Order>())
        }

        val vm = com.restaurant.sushimei.frontend.KitchenViewModel(
            orderRepository = dummyRepo,
            autoStartPolling = false
        )

        composeTestRule.setContent {
            com.restaurant.sushimei.frontend.ui.screens.OperationalOrderCard(summary, detail, vm)
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("SIN: Alga (Por fuera), Surimi", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("NOTA: Poca salsa", substring = true).assertIsDisplayed()

        // Line 2 has no omissions or notes, so "SIN:" and "NOTA:" should only appear exactly once
        composeTestRule.onAllNodesWithText("SIN:", substring = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("NOTA:", substring = true).assertCountEquals(1)
    }
    @Test
    fun testConfiguratorDuplicateKeyCrashPrevention() {
        // Reproduce the scenario for menuItemId = 24 where a Component and a Group both share ID = 36.
        val collidingId = 36L

        // Component with id = 36
        val collidingComponent = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(
            id = collidingId, code = "C36", displayName = "Component 36", detail = null,
            includedByDefault = true, removable = true, displayOrder = 1, active = true
        )

        // Group with id = 36
        val collidingGroup = com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto(
            id = collidingId, name = "Group 36", minSelections = 0, maxSelections = 1, allowDuplicates = false,
            options = listOf(
                com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto(
                    menuItemId = 99L, name = "Option 99", requiresConfiguration = false,
                    catalogPrice = java.math.BigDecimal.ZERO, category = "", available = true,
                    priceAdjustment = java.math.BigDecimal.ZERO
                )
            )
        )

        val testConfig = com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto(
            menuItemId = 24L, name = "Promoted Roll", standaloneOrderable = true, basePrice = java.math.BigDecimal.TEN,
            requiresConfiguration = false, groups = listOf(collidingGroup)
        )

        val repo = object : com.restaurant.sushimei.frontend.data.repository.IMenuRepository {
            override fun observeAll() = kotlinx.coroutines.flow.flowOf<List<com.restaurant.sushimei.frontend.data.model.MenuItem>>(emptyList())
            override fun observeActive() = kotlinx.coroutines.flow.flowOf<List<com.restaurant.sushimei.frontend.data.model.MenuItem>>(emptyList())
            override fun observeActiveCategories() = kotlinx.coroutines.flow.flowOf<List<String>>(emptyList())
            override suspend fun refreshCatalog(standaloneOnly: Boolean?) {}
            override suspend fun getCategories() = emptyList<String>()
            override suspend fun getProducts() = emptyList<com.restaurant.sushimei.frontend.data.model.MenuItem>()
            override suspend fun createProduct(request: com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse = TODO()
            override suspend fun updateProduct(id: Long, request: com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse = TODO()
            override suspend fun deleteProduct(id: Long) {}
            override suspend fun setActive(id: Long, activo: Boolean) {}
            override suspend fun getTags() = emptyList<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>()
            override suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.TagCreateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto = TODO()
            override suspend fun updateTag(id: Long, tag: com.restaurant.sushimei.frontend.data.model.TagUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto = TODO()
            override suspend fun deleteTag(id: Long) {}
            override suspend fun getConfiguration(menuItemId: Long) = testConfig
            override suspend fun getMenuItemComponents(menuItemId: Long) = listOf(collidingComponent)
            override suspend fun quoteItem(menuItemId: Long, request: com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto) =
                com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto(1L, "Promoted Roll", 1, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN, emptyList(), java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN)
        }

        val vm = com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorViewModel(repo)

        // Initialize as a promoted Root item
        val promotedConfiguredProduct = com.restaurant.sushimei.frontend.data.model.ConfiguredProduct(
            id = "cart-1",
            menuItemId = 24L,
            name = "Promoted Roll",
            quantity = 1,
            baseUnitPrice = java.math.BigDecimal.TEN,
            unitTotal = java.math.BigDecimal.TEN,
            total = java.math.BigDecimal.TEN,
            groups = emptyList(), // Provide empty or mapped groups to simulate existing config
            omittedComponents = emptyList(),
            note = "",
            promotionSelection = com.restaurant.sushimei.frontend.data.model.PromotionLineSelection(
                promotionId = 100L,
                promotionName = "$69 Promo"
            )
        )

        vm.loadConfiguration(24L, promotedConfiguredProduct)

        composeTestRule.setContent {
            com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorScreen(
                menuItemId = 24L,
                viewModel = vm,
                onDismiss = { },
                onAddToCart = { }
            )
        }

        composeTestRule.waitForIdle()

        // If keys collided, composeTestRule.setContent would throw IllegalArgumentException: Key "36" was already used
        // Since we reach here, it successfully mounted. Now verify the UI rows exist:
        composeTestRule.onNodeWithText("Component 36", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Group 36", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Option 99", substring = true).assertIsDisplayed()
    }
}

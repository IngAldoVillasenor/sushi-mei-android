package com.restaurant.sushimei.frontend.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.ItemPricingMode
import com.restaurant.sushimei.frontend.ui.screens.MenuItemCard
import com.restaurant.sushimei.frontend.ui.screens.OpenSaleDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

@RunWith(AndroidJUnit4::class)
class Mvp2ComposeSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun testOpenSaleDialogRenders() {
        var submitted = false
        composeTestRule.setContent {
            OpenSaleDialog(
                onDismiss = {},
                onSubmit = { _, _, _, _ -> submitted = true }
            )
        }

        // Verify primary fields are rendered
        composeTestRule.onNodeWithText("Venta Libre").assertIsDisplayed()
        composeTestRule.onNodeWithText("Descripción").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monto").assertIsDisplayed()
        // Default PaymentMethod is CASH, so Efectivo Recibido should be visible
        composeTestRule.onNodeWithText("Efectivo Recibido").assertIsDisplayed()
        // Switch to CARD, verify the option exists
        composeTestRule.onNodeWithText("CARD").assertIsDisplayed()
        composeTestRule.onNodeWithText("CARD").performClick()
    }
}

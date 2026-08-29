package com.restaurant.sushimei.frontend

import android.content.Context
import com.restaurant.sushimei.frontend.data.model.FulfillmentType
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderLineDto
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant

class PrintServiceTest {
    @Test
    fun `test print output for DELIVERY and CASH with denomination`() {
        val detail = OperationalOrderDetailDto(
            id = 100,
            requestId = "req-1",
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = FulfillmentType.DELIVERY,
            paymentMethod = PaymentMethod.CASH,
            deliveryAddress = "Av. Siempre Viva 742",
            pickupName = null,
            cashDenomination = BigDecimal("500.00"),
            phoneNumber = "555-1234",
            status = "PENDING",
            createdAt = Instant.now(),
            total = BigDecimal("350.00"),
            legacyOrderDetails = null,
            paymentNotes = null,
            transferReceiptPath = null,
            lines = listOf(
                OperationalOrderLineDto(
                    id = 101,
                    lineKind = "STANDARD",
                    lineKey = "uuid-1",
                    sourceMenuItemId = 99,
                    name = "Sushi Roll",
                    quantity = 2,
                    catalogBaseUnitPrice = BigDecimal("175.00"),
                    chargedBaseUnitPrice = BigDecimal("175.00"),
                    configurationAdjustmentAmount = BigDecimal.ZERO,
                    finalUnitAmount = BigDecimal("175.00"),
                    finalLineTotal = BigDecimal("350.00"),
                    configuration = emptyList(),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null
                )
            )
        )

        val context = mockk<Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail))

        assertTrue("Output should contain delivery address", outputString.contains("Direccion: Av. Siempre Viva 742"))
        assertTrue("Output should contain cash payment method", outputString.contains("Pago: CASH"))
        assertTrue("Output should contain cash denomination", outputString.contains("Paga con: $500.00"))
        assertTrue("Output should contain exact change", outputString.contains("Cambio: $150.00"))
        assertTrue("Output should contain ordered items", outputString.contains("2x Sushi Roll"))
        assertTrue("Output should contain phone number", outputString.contains("Telefono: 555-1234"))
    }

    @Test
    fun `test print output for legacy fallback`() {
        val detail = OperationalOrderDetailDto(
            id = 101,
            requestId = null,
            orderSource = null,
            createdByUserId = null,
            fulfillmentType = null, // Unknown/legacy
            paymentMethod = null, // Unknown/legacy
            deliveryAddress = null,
            pickupName = null,
            cashDenomination = null,
            phoneNumber = null,
            status = "PENDING",
            createdAt = Instant.now(),
            total = BigDecimal("150.00"),
            legacyOrderDetails = "2x Maki\n1x Sake",
            paymentNotes = null,
            transferReceiptPath = null,
            lines = emptyList() // Empty lines forces fallback
        )

        val context = mockk<Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail))

        assertTrue("Output should fallback to legacy unknown", outputString.contains("Tipo: LEGACY/DESCONOCIDO"))
        assertTrue("Output should contain fallback legacy text", outputString.contains("2x Maki"))
        assertTrue("Output should handle unknown payment", outputString.contains("Pago: DESCONOCIDO"))
    }

    @Test
    fun `test print output for PICKUP`() {
        val detail = OperationalOrderDetailDto(
            id = 102,
            requestId = null,
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = FulfillmentType.PICKUP,
            paymentMethod = PaymentMethod.CARD,
            deliveryAddress = null,
            pickupName = "John Doe",
            cashDenomination = null,
            phoneNumber = null,
            status = "PENDING",
            createdAt = Instant.now(),
            total = BigDecimal("150.00"),
            legacyOrderDetails = null,
            paymentNotes = null,
            transferReceiptPath = null,
            lines = emptyList()
        )

        val context = mockk<Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail))

        assertTrue("Output should indicate MOSTRADOR", outputString.contains("Tipo: MOSTRADOR"))
        assertTrue("Output should contain pickup name", outputString.contains("Nombre: John Doe"))
        assertTrue("Output should contain card payment method", outputString.contains("Pago: CARD"))
    }

    @Test
    fun `test print output gives precedence to structured lines over legacyOrderDetails`() {
        val detail = OperationalOrderDetailDto(
            id = 103,
            requestId = null,
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = FulfillmentType.PICKUP,
            paymentMethod = PaymentMethod.CARD,
            deliveryAddress = null,
            pickupName = "Jane Doe",
            cashDenomination = null,
            phoneNumber = null,
            status = "PENDING",
            createdAt = Instant.now(),
            total = BigDecimal("150.00"),
            legacyOrderDetails = "LEGACY FALLBACK DO NOT PRINT",
            paymentNotes = null,
            transferReceiptPath = null,
            lines = listOf(
                OperationalOrderLineDto(
                    id = 101,
                    lineKind = "STANDARD",
                    lineKey = "uuid-1",
                    sourceMenuItemId = 99,
                    name = "Structured Sushi",
                    quantity = 2,
                    catalogBaseUnitPrice = BigDecimal("175.00"),
                    chargedBaseUnitPrice = BigDecimal("175.00"),
                    configurationAdjustmentAmount = BigDecimal.ZERO,
                    finalUnitAmount = BigDecimal("175.00"),
                    finalLineTotal = BigDecimal("350.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    configuration = emptyList()
                )
            )
        )

        val context = mockk<Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail))

        assertTrue("Output should contain structured item name", outputString.contains("Structured Sushi"))
        assertTrue("Output should NOT contain legacy fallback string", !outputString.contains("LEGACY FALLBACK DO NOT PRINT"))
    }
    @Test
    fun `test recursive displayOnTicket`() {
        val detail = OperationalOrderDetailDto(
            id = 100,
            requestId = "req-1",
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = FulfillmentType.DELIVERY,
            paymentMethod = PaymentMethod.CASH,
            deliveryAddress = "Av. Siempre Viva 742",
            pickupName = null,
            cashDenomination = BigDecimal("500.00"),
            phoneNumber = "555-1234",
            status = "PENDING",
            createdAt = Instant.now(),
            total = BigDecimal("350.00"),
            legacyOrderDetails = null,
            paymentNotes = null,
            transferReceiptPath = null,
            lines = listOf(
                OperationalOrderLineDto(
                    id = 101,
                    lineKind = "STANDARD",
                    lineKey = "uuid-1",
                    sourceMenuItemId = 99,
                    name = "Combo",
                    quantity = 1,
                    catalogBaseUnitPrice = BigDecimal("175.00"),
                    chargedBaseUnitPrice = BigDecimal("175.00"),
                    configurationAdjustmentAmount = BigDecimal.ZERO,
                    finalUnitAmount = BigDecimal("175.00"),
                    finalLineTotal = BigDecimal("350.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    configuration = listOf(
                        com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                            id = 1L,
                            parentSelectionSnapshotId = null,
                            groupId = 1L,
                            groupName = "Box",
                            selectionPosition = 1,
                            menuItemId = 100L,
                            itemName = "Virtual Box (Hidden)",
                            displayOnTicket = false,
                            quantity = 1,
                            catalogUnitPrice = BigDecimal.ZERO,
                            priceAdjustment = BigDecimal.ZERO
                        ),
                        com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                            id = 2L,
                            parentSelectionSnapshotId = 1L,
                            groupId = 2L,
                            groupName = "Drinks",
                            selectionPosition = 1,
                            menuItemId = 101L,
                            itemName = "Coke",
                            displayOnTicket = true,
                            quantity = 1,
                            catalogUnitPrice = BigDecimal.ZERO,
                            priceAdjustment = BigDecimal.ZERO
                        )
                    )
                )
            )
        )

        val context = mockk<Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail))

        assertTrue(!outputString.contains("Virtual Box (Hidden)"))
        assertTrue(outputString.contains("   1x Coke")) // Level 1 indentation because parent was level 1
    }

    @Test
    fun `test formatOperationalTicket includes REIMPRESION when isReprint is true`() {
        val detail = com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto(
            id = 200,
            requestId = null,
            orderSource = null,
            createdByUserId = null,
            fulfillmentType = null,
            paymentMethod = null,
            deliveryAddress = null,
            pickupName = null,
            cashDenomination = null,
            phoneNumber = null,
            status = "PENDING",
            createdAt = java.time.Instant.now(),
            total = java.math.BigDecimal("150.00"),
            legacyOrderDetails = "2x Maki",
            paymentNotes = null,
            transferReceiptPath = null,
            lines = emptyList()
        )

        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail, isReprint = true))

        org.junit.Assert.assertTrue("Output should contain REIMPRESION", outputString.contains("*** REIMPRESION ***"))
    }

    @Test
    fun `test formatOperationalTicket omits REIMPRESION when isReprint is false`() {
        val detail = com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto(
            id = 201,
            requestId = null,
            orderSource = null,
            createdByUserId = null,
            fulfillmentType = null,
            paymentMethod = null,
            deliveryAddress = null,
            pickupName = null,
            cashDenomination = null,
            phoneNumber = null,
            status = "PENDING",
            createdAt = java.time.Instant.now(),
            total = java.math.BigDecimal("150.00"),
            legacyOrderDetails = "2x Maki",
            paymentNotes = null,
            transferReceiptPath = null,
            lines = emptyList()
        )

        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail, isReprint = false))

        org.junit.Assert.assertFalse("Output should not contain REIMPRESION", outputString.contains("*** REIMPRESION ***"))
    }


    @Test
    fun `test formatOperationalTicket includes omitted components and notes`() {
        val detail = OperationalOrderDetailDto(
            id = 104,
            requestId = null,
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = FulfillmentType.DELIVERY,
            paymentMethod = PaymentMethod.CASH,
            deliveryAddress = "Av. Siempre Viva 742",
            pickupName = null,
            cashDenomination = BigDecimal("500.00"),
            phoneNumber = null,
            status = "PENDING",
            createdAt = Instant.now(),
            total = BigDecimal("350.00"),
            legacyOrderDetails = null,
            paymentNotes = null,
            transferReceiptPath = null,
            lines = listOf(
                OperationalOrderLineDto(
                    id = 1,
                    lineKind = "ITEM",
                    lineKey = "key1",
                    sourceMenuItemId = 1L,
                    name = "California roll",
                    quantity = 1,
                    catalogBaseUnitPrice = BigDecimal("120.00"),
                    chargedBaseUnitPrice = BigDecimal("120.00"),
                    configurationAdjustmentAmount = BigDecimal.ZERO,
                    finalUnitAmount = BigDecimal("120.00"),
                    finalLineTotal = BigDecimal("120.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    omittedComponents = listOf(
                        com.restaurant.sushimei.frontend.data.model.OrderComponentOmissionSnapshotDto(
                            id = 2, sourceComponentId = 2L, code = "ALG", displayName = "Alga", detail = "Por fuera", displayOrder = 1
                        ),
                        com.restaurant.sushimei.frontend.data.model.OrderComponentOmissionSnapshotDto(
                            id = 3, sourceComponentId = 3L, code = "SUR", displayName = "Surimi", detail = null, displayOrder = 2
                        )
                    ),
                    note = "Poca salsa",
                    configuration = listOf(
                        com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                            id = 10L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 1, menuItemId = 2L,
                            itemName = "Tampico", priceAdjustment = BigDecimal.ZERO, displayOnTicket = true, quantity = 1, catalogUnitPrice = BigDecimal.ZERO
                        )
                    )
                ),
                OperationalOrderLineDto(
                    id = 2,
                    lineKind = "ITEM",
                    lineKey = "key2",
                    sourceMenuItemId = 2L,
                    name = "Agua",
                    quantity = 1,
                    catalogBaseUnitPrice = BigDecimal("20.00"),
                    chargedBaseUnitPrice = BigDecimal("20.00"),
                    configurationAdjustmentAmount = BigDecimal.ZERO,
                    finalUnitAmount = BigDecimal("20.00"),
                    finalLineTotal = BigDecimal("20.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    omittedComponents = emptyList(),
                    note = null,
                    configuration = emptyList()
                )
            )
        )

        val context = mockk<Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(detail))

        org.junit.Assert.assertTrue(outputString.contains("SIN: Alga (Por fuera), Surimi"))
        org.junit.Assert.assertTrue(outputString.contains("NOTA: Poca salsa"))
        org.junit.Assert.assertTrue(outputString.contains("1x Tampico"))

        // No empty labels
        val secondItemIndex = outputString.indexOf("1x Agua")
        val subsequentText = outputString.substring(secondItemIndex)
        assertFalse(subsequentText.contains("SIN: \n"))
        assertFalse(subsequentText.contains("NOTA: \n"))
        assertFalse(subsequentText.contains("SIN: \r\n"))
        assertFalse(subsequentText.contains("NOTA: \r\n"))
    }

    private fun createBaseOrder(configList: List<com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto>): OperationalOrderDetailDto {
        return OperationalOrderDetailDto(
            id = 1,
            requestId = "req-1",
            orderSource = "POS",
            createdByUserId = 1,
            fulfillmentType = FulfillmentType.DELIVERY,
            paymentMethod = PaymentMethod.CASH,
            deliveryAddress = null,
            pickupName = null,
            cashDenomination = null,
            phoneNumber = null,
            status = "PENDING",
            createdAt = java.time.Instant.now(),
            total = java.math.BigDecimal("100.00"),
            legacyOrderDetails = null,
            paymentNotes = null,
            transferReceiptPath = null,
            lines = listOf(
                OperationalOrderLineDto(
                    id = 1,
                    lineKind = "ITEM",
                    lineKey = "key1",
                    sourceMenuItemId = 1L,
                    name = "Charola Supreme",
                    quantity = 1,
                    catalogBaseUnitPrice = java.math.BigDecimal("100.00"),
                    chargedBaseUnitPrice = java.math.BigDecimal("100.00"),
                    configurationAdjustmentAmount = java.math.BigDecimal.ZERO,
                    finalUnitAmount = java.math.BigDecimal("100.00"),
                    finalLineTotal = java.math.BigDecimal("100.00"),
                    promotion = null,
                    rewardOrdinal = null,
                    sourcePaidLineId = null,
                    omittedComponents = emptyList(),
                    note = null,
                    configuration = configList
                )
            )
        )
    }

    @Test
    fun `TEST 1 - ticket formatting aggregates configurations with distinct quantities`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 1L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 1, menuItemId = 101L,
                itemName = "California", quantity = 2, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 2L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 2, menuItemId = 102L,
                itemName = "Empanizado", quantity = 2, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 3L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 3, menuItemId = 103L,
                itemName = "Banana Roll", quantity = 1, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO
            )
        )
        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(order))

        org.junit.Assert.assertTrue(outputString.contains("2x California"))
        org.junit.Assert.assertTrue(outputString.contains("2x Empanizado"))
        org.junit.Assert.assertTrue(outputString.contains("1x Banana Roll"))
    }

    @Test
    fun `TEST 2 - single configured selection explicitly displays 1x`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 1L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 1, menuItemId = 101L,
                itemName = "California", quantity = 1, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO
            )
        )
        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(order))

        org.junit.Assert.assertTrue(outputString.contains("1x California"))
    }

    @Test
    fun `TEST 3 - duplicate equivalent snapshots are aggregated for presentation`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 1L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 1, menuItemId = 101L,
                itemName = "California", quantity = 1, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 2L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 2, menuItemId = 101L,
                itemName = "California", quantity = 1, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO
            )
        )
        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(order))

        org.junit.Assert.assertTrue(outputString.contains("2x California"))
    }

    @Test
    fun `TEST 4 - selections with identical menu item but distinct notes are not aggregated`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 1L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 1, menuItemId = 101L,
                itemName = "California", quantity = 1, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                note = null
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 2L, parentSelectionSnapshotId = null, groupId = 1L, groupName = "Group", selectionPosition = 2, menuItemId = 101L,
                itemName = "California", quantity = 1, displayOnTicket = true, catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                note = "Sin aguacate"
            )
        )
        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(order))

        val count = outputString.split("1x California").size - 1
        org.junit.Assert.assertEquals("Should output 1x California twice independently", 2, count)
        org.junit.Assert.assertTrue(outputString.contains("NOTA: Sin aguacate"))
    }

    @Test
    fun `TEST 5 - selections with same omission sourceComponentId but different detail are not aggregated`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 1L,
                parentSelectionSnapshotId = null,
                groupId = 1L,
                groupName = "Base",
                selectionPosition = 1,
                menuItemId = 10L,
                itemName = "Tampico",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = listOf(
                    com.restaurant.sushimei.frontend.data.model.OrderComponentOmissionSnapshotDto(
                        id = 100L,
                        sourceComponentId = 99L,
                        code = "MAYONNAISE",
                        displayName = "Mayonnaise",
                        detail = "Spicy",
                        displayOrder = 1
                    )
                ),
                note = null
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 2L,
                parentSelectionSnapshotId = null,
                groupId = 1L,
                groupName = "Base",
                selectionPosition = 2,
                menuItemId = 10L,
                itemName = "Tampico",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = listOf(
                    com.restaurant.sushimei.frontend.data.model.OrderComponentOmissionSnapshotDto(
                        id = 101L,
                        sourceComponentId = 99L,
                        code = "MAYONNAISE",
                        displayName = "Mayonnaise",
                        detail = "Mild",
                        displayOrder = 1
                    )
                ),
                note = null
            )
        )

        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(order))

        org.junit.Assert.assertTrue(outputString.contains("1x Tampico"))
        org.junit.Assert.assertFalse(outputString.contains("2x Tampico"))
        org.junit.Assert.assertTrue(outputString.contains("SIN: Mayonnaise (Spicy)"))
        org.junit.Assert.assertTrue(outputString.contains("SIN: Mayonnaise (Mild)"))
    }

    @Test
    fun `TEST 6 - parent selections with identical details but different child configuration are not aggregated`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 10L,
                parentSelectionSnapshotId = null,
                groupId = 1L,
                groupName = "Rolls",
                selectionPosition = 1,
                menuItemId = 100L,
                itemName = "California",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = emptyList(),
                note = null
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 11L,
                parentSelectionSnapshotId = 10L, // Child of first California
                groupId = 2L,
                groupName = "Extras",
                selectionPosition = 1,
                menuItemId = 200L,
                itemName = "Extra Cheese",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = emptyList(),
                note = null
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 20L,
                parentSelectionSnapshotId = null,
                groupId = 1L,
                groupName = "Rolls",
                selectionPosition = 2,
                menuItemId = 100L,
                itemName = "California",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = emptyList(),
                note = null
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 21L,
                parentSelectionSnapshotId = 20L, // Child of second California
                groupId = 2L,
                groupName = "Extras",
                selectionPosition = 1,
                menuItemId = 201L,
                itemName = "Extra Avocado",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = emptyList(),
                note = null
            )
        )

        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val outputString = String(PrintService(context).formatOperationalTicket(order))

        org.junit.Assert.assertTrue(outputString.contains("1x California"))
        org.junit.Assert.assertFalse(outputString.contains("2x California"))
        org.junit.Assert.assertTrue(outputString.contains("1x Extra Cheese"))
        org.junit.Assert.assertTrue(outputString.contains("1x Extra Avocado"))
    }

    @Test
    fun `TEST 7 - quantity rendering is identical in normal and reprint tickets apart from REIMPRESION header`() {
        val configList = listOf(
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 1L,
                parentSelectionSnapshotId = null,
                groupId = 1L,
                groupName = "Rolls",
                selectionPosition = 1,
                menuItemId = 100L,
                itemName = "California",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = emptyList(),
                note = null
            ),
            com.restaurant.sushimei.frontend.data.model.OrderConfigurationSnapshotDto(
                id = 2L,
                parentSelectionSnapshotId = null,
                groupId = 1L,
                groupName = "Rolls",
                selectionPosition = 2,
                menuItemId = 100L,
                itemName = "California",
                quantity = 1,
                catalogUnitPrice = java.math.BigDecimal.ZERO, priceAdjustment = java.math.BigDecimal.ZERO,
                displayOnTicket = true,
                omittedComponents = emptyList(),
                note = null
            )
        )

        val order = createBaseOrder(configList)
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)

        val normalOutput = String(PrintService(context).formatOperationalTicket(order))
        val reprintOutput = String(PrintService(context).formatOperationalTicket(order, isReprint = true))

        val normalBody = normalOutput.substringAfter("Ticket: ")
        val reprintBody = reprintOutput.substringAfter("Ticket: ")

        org.junit.Assert.assertTrue(normalOutput.contains("2x California"))
        org.junit.Assert.assertTrue(reprintOutput.contains("2x California"))
        org.junit.Assert.assertTrue(reprintOutput.contains("*** REIMPRESION ***"))
        org.junit.Assert.assertEquals(normalBody, reprintBody)
    }
}

package com.restaurant.sushimei.frontend

import android.content.Context
import com.restaurant.sushimei.frontend.data.model.FulfillmentType
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderLineDto
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import org.junit.Assert.assertTrue
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
}

package com.restaurant.sushimei.frontend.data.api

import com.google.gson.Gson
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderResponse
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class OperationalOrderDtoTest {
    private val gson: Gson = NetworkModule.configuredGson

    @Test
    fun `test ManualPosOrderResponse deserialization with exact realistic payload`() {
        val json = """
            {
                "id": 100,
                "requestId": "123e4567-e89b-12d3-a456-426614174000",
                "result": "CREATED",
                "orderSource": "ANDROID_MANUAL",
                "createdByUserId": 1,
                "fulfillmentType": "PICKUP",
                "paymentMethod": "CASH",
                "pickupName": "Aldo",
                "status": "PENDING",
                "createdAt": "2026-08-11T12:00:00Z",
                "total": 150.00,
                "lines": [
                    {
                        "id": 101,
                        "lineKind": "PAID",
                        "lineKey": "uuid-1",
                        "sourceMenuItemId": 99,
                        "name": "Maki",
                        "quantity": 2,
                        "catalogBaseUnitPrice": 70.00,
                        "chargedBaseUnitPrice": 70.00,
                        "configurationAdjustmentAmount": 5.00,
                        "finalUnitAmount": 75.00,
                        "finalLineTotal": 150.00,
                        "promotion": {
                            "id": 10,
                            "name": "2x1 Maki",
                            "benefitType": "DISCOUNT"
                        },
                        "rewardOrdinal": null,
                        "configuration": [
                            {
                                "id": 1,
                                "groupId": 10,
                                "groupName": "Extras",
                                "selectionPosition": 1,
                                "menuItemId": 101,
                                "itemName": "Salsa",
                                "quantity": 1,
                                "catalogUnitPrice": 5.00,
                                "priceAdjustment": 5.00
                            }
                        ],
                        "rewards": [
                            {
                                "id": 102,
                                "lineKind": "PROMOTION_REWARD",
                                "lineKey": null,
                                "sourceMenuItemId": 99,
                                "name": "Free Maki",
                                "quantity": 1,
                                "catalogBaseUnitPrice": 70.00,
                                "chargedBaseUnitPrice": 0.00,
                                "configurationAdjustmentAmount": 0.00,
                                "finalUnitAmount": 0.00,
                                "finalLineTotal": 0.00,
                                "promotion": null,
                                "rewardOrdinal": 1,
                                "configuration": [],
                                "rewards": []
                            }
                        ]
                    }
                ]
            }
        """

        val response = gson.fromJson(json, ManualPosOrderResponse::class.java)
        assertNotNull(response)
        assertEquals("ANDROID_MANUAL", response.orderSource)

        val line = response.lines.first()
        assertEquals("PAID", line.lineKind)
        assertEquals("2x1 Maki", line.promotion?.name)
        assertEquals("DISCOUNT", line.promotion?.benefitType)
        assertNull(line.rewardOrdinal)

        val reward = line.rewards.first()
        assertEquals("PROMOTION_REWARD", reward.lineKind)
        assertNull(reward.lineKey)
        assertEquals(1, reward.rewardOrdinal)
        assertEquals(0, BigDecimal("0.00").compareTo(reward.finalLineTotal))
    }

    @Test
    fun `test OperationalOrderSummaryDto deserialization with nullable metadata and Instant`() {
        val json = """
            {
                "id": 200,
                "orderSource": null,
                "status": "PREPARING",
                "fulfillmentType": null,
                "paymentMethod": null,
                "deliveryAddress": null,
                "pickupName": null,
                "cashDenomination": null,
                "phoneNumber": null,
                "total": null,
                "createdAt": null,
                "requiresPaymentValidation": false,
                "structuredLinesAvailable": false
            }
        """
        val summary = gson.fromJson(json, OperationalOrderSummaryDto::class.java)
        assertNotNull(summary)
        assertNull(summary.orderSource)
        assertNull(summary.fulfillmentType)
        assertNull(summary.total)
        assertNull(summary.createdAt)
    }

    @Test
    fun `test OperationalOrderSummaryDto deserialization with modern Instant`() {
        val json = """
            {
                "id": 201,
                "orderSource": "ANDROID_MANUAL",
                "status": "PREPARING",
                "fulfillmentType": "DELIVERY",
                "paymentMethod": "CASH",
                "deliveryAddress": "123 Main St",
                "pickupName": null,
                "cashDenomination": 500.0,
                "phoneNumber": "555-1234",
                "total": 350.0,
                "createdAt": "2026-08-11T12:00:00Z",
                "requiresPaymentValidation": false,
                "structuredLinesAvailable": true
            }
        """
        val summary = gson.fromJson(json, OperationalOrderSummaryDto::class.java)
        assertNotNull(summary)
        assertEquals(Instant.parse("2026-08-11T12:00:00Z"), summary.createdAt)
    }

    @Test
    fun `test legacy OperationalOrderDetailDto fallback properties`() {
        val json = """
            {
                "id": 200,
                "requestId": null,
                "orderSource": null,
                "createdByUserId": null,
                "fulfillmentType": null,
                "paymentMethod": null,
                "deliveryAddress": null,
                "pickupName": null,
                "cashDenomination": null,
                "phoneNumber": null,
                "transferReceiptPath": null,
                "paymentNotes": null,
                "status": "PREPARING",
                "createdAt": "2026-08-11T12:30:00Z",
                "total": null,
                "legacyOrderDetails": "2x Maki\n1x Sake",
                "lines": []
            }
        """

        val detail = gson.fromJson(json, OperationalOrderDetailDto::class.java)
        assertNotNull(detail)
        assertNull(detail.requestId)
        assertNull(detail.orderSource)
        assertNull(detail.fulfillmentType)
        assertNull(detail.paymentMethod)
        assertNull(detail.total)
        assertEquals(Instant.parse("2026-08-11T12:30:00Z"), detail.createdAt)
        assertEquals("2x Maki\n1x Sake", detail.legacyOrderDetails)
        assertEquals(true, detail.lines.isEmpty())
    }
}

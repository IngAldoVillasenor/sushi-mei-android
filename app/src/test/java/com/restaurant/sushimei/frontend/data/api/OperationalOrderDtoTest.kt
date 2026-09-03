package com.restaurant.sushimei.frontend.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderResponse
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest
import com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse
import com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionRequest
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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

    @Test
    fun `ManualPosOrderRequest ON_DELIVERY serialization - Gson produces null paymentMethod and correct timing`() {
        val req = com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest(
            requestId = "req-od-1",
            fulfillmentType = com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY,
            paymentMethod = null,
            paymentTiming = com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming.ON_DELIVERY,
            deliveryAddress = "Test Address 1",
            pickupName = null,
            cashDenomination = null,
            lines = emptyList(),
            manualLines = emptyList()
        )
        val json = gson.toJson(req)
        val obj = com.google.gson.JsonParser.parseString(json).asJsonObject

        assertEquals("DELIVERY", obj.get("fulfillmentType").asString)
        assertEquals("ON_DELIVERY", obj.get("paymentTiming").asString)
        assertTrue("paymentMethod must be absent", !obj.has("paymentMethod"))
        assertTrue("cashDenomination must be absent", !obj.has("cashDenomination"))
    }

    @Test
    fun `ManualPosOrderResponse ON_DELIVERY deserialization - realistic Backend PR 41 payload`() {
        val json = """
            {
                "id": 42,
                "requestId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                "result": "CREATED",
                "orderSource": "ANDROID_MANUAL",
                "createdByUserId": 5,
                "fulfillmentType": "DELIVERY",
                "paymentMethod": null,
                "paymentTiming": "ON_DELIVERY",
                "requiresPaymentCollection": true,
                "paymentCollectedAt": null,
                "paymentCollectedByUserId": null,
                "deliveryAddress": "Calle Falsa 123",
                "pickupName": null,
                "cashDenomination": null,
                "status": "PREPARING",
                "createdAt": "2026-09-01T20:00:00Z",
                "total": 250.00,
                "lines": []
            }
        """
        val response = gson.fromJson(json, ManualPosOrderResponse::class.java)

        assertEquals(42L, response.id)
        assertEquals("ANDROID_MANUAL", response.orderSource)
        assertEquals(com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY, response.fulfillmentType)
        assertNull("paymentMethod must be null", response.paymentMethod)
        assertEquals(com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming.ON_DELIVERY, response.paymentTiming)
        assertEquals(true, response.requiresPaymentCollection)
        assertNull("paymentCollectedAt must be null", response.paymentCollectedAt)
        assertNull("paymentCollectedByUserId must be null", response.paymentCollectedByUserId)
    }

    @Test
    fun `OperationalOrderSummaryDto deserialization with paymentTiming and requiresPaymentCollection`() {
        val json = """
            {
                "id": 55,
                "orderSource": "ANDROID_MANUAL",
                "status": "READY",
                "fulfillmentType": "DELIVERY",
                "paymentMethod": null,
                "paymentTiming": "ON_DELIVERY",
                "requiresPaymentCollection": true,
                "deliveryAddress": "Av. Principal 456",
                "pickupName": null,
                "cashDenomination": null,
                "phoneNumber": null,
                "total": 180.00,
                "createdAt": "2026-09-01T19:00:00Z",
                "requiresPaymentValidation": false,
                "structuredLinesAvailable": true
            }
        """
        val summary = gson.fromJson(json, OperationalOrderSummaryDto::class.java)

        assertEquals(55L, summary.id)
        assertEquals(com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming.ON_DELIVERY, summary.paymentTiming)
        assertEquals(true, summary.requiresPaymentCollection)
        assertNull(summary.paymentMethod)
        assertEquals(Instant.parse("2026-09-01T19:00:00Z"), summary.createdAt)
    }

    @Test
    fun `OperationalOrderDetailDto deserialization with settled ON_DELIVERY fields`() {
        val json = """
            {
                "id": 55,
                "requestId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12",
                "orderSource": "ANDROID_MANUAL",
                "createdByUserId": 3,
                "fulfillmentType": "DELIVERY",
                "paymentMethod": "CASH",
                "paymentTiming": "ON_DELIVERY",
                "requiresPaymentCollection": false,
                "paymentCollectedAt": "2026-09-01T21:00:00Z",
                "paymentCollectedByUserId": 7,
                "deliveryAddress": "Calle Veracruz 88",
                "pickupName": null,
                "cashDenomination": 200.00,
                "phoneNumber": null,
                "transferReceiptPath": null,
                "paymentNotes": null,
                "status": "COMPLETED",
                "createdAt": "2026-09-01T19:00:00Z",
                "total": 175.50,
                "legacyOrderDetails": null,
                "lines": []
            }
        """
        val detail = gson.fromJson(json, OperationalOrderDetailDto::class.java)

        assertEquals(55L, detail.id)
        assertEquals(com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH, detail.paymentMethod)
        assertEquals(com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming.ON_DELIVERY, detail.paymentTiming)
        assertEquals(false, detail.requiresPaymentCollection)
        assertNotNull("paymentCollectedAt must be present", detail.paymentCollectedAt)
        assertEquals(7L, detail.paymentCollectedByUserId)
        assertEquals(Instant.parse("2026-09-01T21:00:00Z"), detail.paymentCollectedAt)
    }

    @Test
    fun `OrderPaymentCollectionRequest serialization via Gson`() {
        val req = com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionRequest(
            paymentMethod = com.restaurant.sushimei.frontend.data.model.PaymentMethod.TRANSFER,
            cashDenomination = null
        )
        val json = gson.toJson(req)
        val obj = com.google.gson.JsonParser.parseString(json).asJsonObject

        assertEquals("TRANSFER", obj.get("paymentMethod").asString)
        assertTrue("cashDenomination must be absent", !obj.has("cashDenomination"))
    }

    @Test
    fun `OrderPaymentCollectionResponse deserialization with all required fields`() {
        val json = """
            {
                "orderId": 55,
                "previousStatus": "READY",
                "currentStatus": "COMPLETED",
                "paymentTiming": "ON_DELIVERY",
                "paymentMethod": "TRANSFER",
                "cashDenomination": null,
                "paymentCollectedAt": "2026-09-01T21:00:00Z",
                "paymentCollectedByUserId": 7
            }
        """
        val response = gson.fromJson(json, com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse::class.java)

        assertEquals(55L, response.orderId)
        assertEquals("READY", response.previousStatus)
        assertEquals("COMPLETED", response.currentStatus)
        assertEquals(com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming.ON_DELIVERY, response.paymentTiming)
        assertEquals(com.restaurant.sushimei.frontend.data.model.PaymentMethod.TRANSFER, response.paymentMethod)
        assertNull(response.cashDenomination)
        assertNotNull(response.paymentCollectedAt)
        assertEquals(7L, response.paymentCollectedByUserId)
    }
}

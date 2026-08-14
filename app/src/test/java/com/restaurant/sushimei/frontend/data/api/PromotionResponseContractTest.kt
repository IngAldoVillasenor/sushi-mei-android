package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.PromotionCreateRequest
import com.restaurant.sushimei.frontend.data.model.PromotionResponse
import com.restaurant.sushimei.frontend.data.model.PromotionTargetRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class PromotionResponseContractTest {

    @Test
    fun `flat promotion response deserializes backend contract`() {
        val json = """
            {
              "id": 8,
              "name": "Jueves 2x1",
              "active": true,
              "priority": 100,
              "benefitType": "BUY_X_GET_Y_SAME_ITEM",
              "fixedUnitPrice": null,
              "buyQuantity": 1,
              "rewardQuantity": 1,
              "repeat": true,
              "validFrom": "2026-08-01",
              "validUntil": null,
              "daysOfWeek": [4],
              "targets": [
                {"targetType": "ITEM", "targetId": 24},
                {"targetType": "ITEM", "targetId": 49}
              ],
              "createdAt": "2026-08-14T01:00:00Z",
              "updatedAt": "2026-08-14T02:00:00Z",
              "version": 3
            }
        """.trimIndent()

        val response = NetworkModule.configuredGson.fromJson(json, PromotionResponse::class.java)

        assertEquals("BUY_X_GET_Y_SAME_ITEM", response.benefitType)
        assertNull(response.fixedUnitPrice)
        assertEquals(1, response.buyQuantity)
        assertEquals(1, response.rewardQuantity)
        assertEquals(setOf(4), response.daysOfWeek)
        assertEquals(LocalDate.of(2026, 8, 1), response.validFrom)
        assertNull(response.validUntil)
        assertEquals(24L, response.targets.first().targetId)
        assertEquals(Instant.parse("2026-08-14T01:00:00Z"), response.createdAt)
    }

    @Test
    fun `promotion create request serializes flat fields only`() {
        val request = PromotionCreateRequest(
            name = "Lunes $69",
            active = true,
            priority = 100,
            benefitType = "FIXED_UNIT_PRICE",
            fixedUnitPrice = BigDecimal("69.00"),
            buyQuantity = null,
            rewardQuantity = null,
            repeat = null,
            validFrom = null,
            validUntil = null,
            daysOfWeek = setOf(1),
            targets = listOf(PromotionTargetRequest("ITEM", 24L))
        )

        val json = NetworkModule.configuredGson.toJsonTree(request).asJsonObject

        assertEquals("FIXED_UNIT_PRICE", json["benefitType"].asString)
        assertEquals(BigDecimal("69.00"), json["fixedUnitPrice"].asBigDecimal)
        assertTrue(json["daysOfWeek"].asJsonArray.any { it.asInt == 1 })
        assertEquals("ITEM", json["targets"].asJsonArray[0].asJsonObject["targetType"].asString)
        assertFalse(json.has("schedule"))
        assertFalse(json.has("benefit"))
        assertFalse(json.has("createdAt"))
        assertFalse(json.has("updatedAt"))
    }
}

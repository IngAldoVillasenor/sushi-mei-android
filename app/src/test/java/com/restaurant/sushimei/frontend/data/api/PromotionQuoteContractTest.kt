package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto
import com.restaurant.sushimei.frontend.data.model.QuoteResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

class PromotionQuoteContractTest {

    private val gson = NetworkModule.configuredGson

    @Test
    fun `test promotion quote deserializes object configuration correctly`() {
        val json = """
            {
              "quotedAt": "2026-08-11T23:59:01.123Z",
              "businessTimeZone": "America/Mexico_City",
              "catalogBaseSubtotal": 110.00,
              "configurationAdjustmentTotal": 0.00,
              "promotionAdjustmentTotal": 0.00,
              "total": 110.00,
              "lines": [
                {
                  "lineKey": "line-1",
                  "menuItemId": 10,
                  "name": "Banana Ebi",
                  "quantity": 1,
                  "catalogBaseUnitPrice": 110.00,
                  "chargedBaseUnitPrice": 110.00,
                  "appliedPromotion": null,
                  "promotionAdjustmentTotal": 0.00,
                  "lineTotal": 110.00,
                  "rewards": [
                    {
                      "sourceLineKey": "line-1",
                      "rewardOrdinal": 0,
                      "promotion": { "id": 1, "name": "Test Promo" },
                      "menuItemId": 20,
                      "name": "Free Roll",
                      "catalogBaseUnitPrice": 50.00,
                      "chargedBaseUnitPrice": 0.00,
                      "configurationAdjustmentTotal": 0.00,
                      "total": 0.00,
                      "configuration": {
                        "menuItemId": 20,
                        "name": "Free Roll",
                        "quantity": 1,
                        "baseUnitPrice": 50.00,
                        "baseTotal": 50.00,
                        "groups": [],
                        "unitAdjustmentTotal": 0.00,
                        "unitTotal": 50.00,
                        "total": 50.00
                      }
                    }
                  ],
                  "configuration": {
                    "menuItemId": 10,
                    "name": "Banana Ebi",
                    "quantity": 1,
                    "baseUnitPrice": 110.00,
                    "baseTotal": 110.00,
                    "groups": [],
                    "unitAdjustmentTotal": 0.00,
                    "unitTotal": 110.00,
                    "total": 110.00
                  }
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, QuoteResponseDto::class.java)

        // Assert normal line configuration
        val line = response.lines[0]
        assertNotNull(line.configuration)
        assertEquals(10L, line.configuration.menuItemId)
        assertEquals("Banana Ebi", line.configuration.name)
        assertEquals(1, line.configuration.quantity)
        assertEquals(BigDecimal("110.00"), line.configuration.unitTotal)
        assertEquals(BigDecimal("110.00"), line.configuration.total)

        // Assert reward configuration
        val reward = line.rewards[0]
        assertNotNull(reward.configuration)
        assertEquals(20L, reward.configuration.menuItemId)
        assertEquals("Free Roll", reward.configuration.name)
        assertEquals(1, reward.configuration.quantity)
        assertEquals(BigDecimal("50.00"), reward.configuration.unitTotal)
        assertEquals(BigDecimal("50.00"), reward.configuration.total)
    }
}

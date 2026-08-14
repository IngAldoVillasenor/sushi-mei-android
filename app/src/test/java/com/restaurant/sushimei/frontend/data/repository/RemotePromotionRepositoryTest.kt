package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.ConfiguredGroup
import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.ConfiguredRewardConfiguration
import com.restaurant.sushimei.frontend.data.model.ConfiguredSelection
import com.restaurant.sushimei.frontend.data.model.PromotionBenefit
import com.restaurant.sushimei.frontend.data.model.PromotionLineSelection
import com.restaurant.sushimei.frontend.data.model.PromotionResponse
import com.restaurant.sushimei.frontend.data.model.PromotionTargetResponse
import com.restaurant.sushimei.frontend.data.model.QuoteRequestDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant

class RemotePromotionRepositoryTest {

    private val api = mockk<SushiMeiApi>()
    private val repository = RemotePromotionRepository(api)

    @Test
    fun `active endpoint maps flat response to domain`() = runTest {
        coEvery { api.getActivePromotions() } returns Response.success(
            listOf(
                PromotionResponse(
                    id = 8L,
                    name = "Jueves 2x1",
                    active = true,
                    priority = 100,
                    benefitType = "BUY_X_GET_Y_SAME_ITEM",
                    fixedUnitPrice = null,
                    buyQuantity = 1,
                    rewardQuantity = 1,
                    repeat = true,
                    validFrom = null,
                    validUntil = null,
                    daysOfWeek = setOf(4),
                    targets = listOf(PromotionTargetResponse("ITEM", 24L)),
                    createdAt = Instant.parse("2026-08-14T01:00:00Z"),
                    updatedAt = Instant.parse("2026-08-14T01:00:00Z"),
                    version = 0L
                )
            )
        )

        val promotion = repository.getActivePromotions().single()

        assertEquals("Jueves 2x1", promotion.name)
        assertEquals(setOf(4), promotion.schedule.daysOfWeek)
        assertEquals(24L, promotion.targets.single().targetId)
        assertTrue(promotion.benefit is PromotionBenefit.BuyXGetYSameItem)
        assertNull(promotion.validFrom)
        coVerify(exactly = 1) { api.getActivePromotions() }
    }

    @Test
    fun `quote request sends reward configuration separately from purchased configuration`() = runTest {
        val requestSlot = slot<QuoteRequestDto>()
        coEvery { api.quotePromotions(capture(requestSlot)) } throws IllegalStateException("captured")
        val purchasedGroup = configuredGroup(10L, 201L)
        val rewardGroup = configuredGroup(20L, 202L)
        val product = ConfiguredProduct(
            id = "line-1",
            menuItemId = 24L,
            name = "California roll",
            quantity = 1,
            baseUnitPrice = BigDecimal("79.00"),
            groups = listOf(purchasedGroup),
            promotionSelection = PromotionLineSelection(
                promotionId = 8L,
                promotionName = "Jueves 2x1",
                rewardConfigurations = listOf(
                    ConfiguredRewardConfiguration(1, listOf(rewardGroup))
                )
            )
        )

        try {
            repository.quoteCart(listOf(product))
        } catch (expected: IllegalStateException) {
            assertEquals("captured", expected.message)
        }

        val line = requestSlot.captured.lines.single()
        assertEquals(10L, line.groups.single().groupId)
        assertEquals(201L, line.groups.single().selections.single().menuItemId)
        assertEquals(1, line.rewardConfigurations.single().rewardOrdinal)
        assertEquals(20L, line.rewardConfigurations.single().groups.single().groupId)
        assertEquals(
            202L,
            line.rewardConfigurations.single().groups.single().selections.single().menuItemId
        )
    }

    private fun configuredGroup(groupId: Long, selectionId: Long) = ConfiguredGroup(
        groupId = groupId,
        name = "Group $groupId",
        selections = listOf(
            ConfiguredSelection(
                menuItemId = selectionId,
                name = "Selection $selectionId",
                quantity = 1,
                catalogUnitPrice = BigDecimal.ZERO,
                priceAdjustment = BigDecimal.ZERO
            )
        )
    )
}

package com.restaurant.sushimei.frontend.ui.admin.promotions

import com.restaurant.sushimei.frontend.data.model.Promotion
import com.restaurant.sushimei.frontend.data.model.PromotionBenefit
import com.restaurant.sushimei.frontend.data.model.PromotionSchedule
import com.restaurant.sushimei.frontend.data.model.PromotionTarget
import com.restaurant.sushimei.frontend.data.model.PromotionTargetType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PromotionEditorStateTest {

    @Test
    fun `editing weekdays preserves every promotion target`() {
        val targets = listOf(18L, 24L, 49L, 80L, 107L).map {
            PromotionTarget(PromotionTargetType.ITEM, it, it.toString())
        }
        val original = Promotion(
            id = 7L,
            name = "Lunes $69",
            active = true,
            priority = 100,
            schedule = PromotionSchedule(setOf(1), allDay = true),
            targets = targets,
            benefit = PromotionBenefit.FixedUnitPrice(BigDecimal("69.00")),
            version = 3L
        )

        val edited = buildPromotionFromEditor(
            originalPromotion = original,
            name = original.name,
            active = original.active,
            daysOfWeek = setOf(1, 5),
            allDay = true,
            targets = original.targets,
            benefit = original.benefit
        )

        assertEquals(setOf(1, 5), edited.schedule.daysOfWeek)
        assertEquals(targets, edited.targets)
        assertEquals(3L, edited.version)
    }
}

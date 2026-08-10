package com.restaurant.sushimei.frontend.data.model

import java.util.UUID

enum class PromotionTargetType {
    TAG,
    ITEM
}

data class PromotionTarget(
    val type: PromotionTargetType,
    val targetId: String,
    val displayName: String
)

data class PromotionSchedule(
    val daysOfWeek: Set<Int>, // 1=Monday... 7=Sunday (java.time.DayOfWeek matches this)
    val allDay: Boolean,
    val startTime: String? = null, // e.g., "14:00"
    val endTime: String? = null    // e.g., "18:00"
)

sealed class PromotionBenefit {
    data class FixedUnitPrice(
        val amount: Double
    ) : PromotionBenefit()

    data class BuyXPayY(
        val buyQuantity: Int,
        val payQuantity: Int,
        val repeat: Boolean
    ) : PromotionBenefit()
}

data class Promotion(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val active: Boolean,
    val priority: Int,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val schedule: PromotionSchedule,
    val target: PromotionTarget,
    val benefit: PromotionBenefit,
    val version: Long = 1L
)

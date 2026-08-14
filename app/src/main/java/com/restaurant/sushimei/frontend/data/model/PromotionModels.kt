package com.restaurant.sushimei.frontend.data.model

import java.math.BigDecimal
import java.time.Instant

// ============================================================================
// D O M A I N   M O D E L S
// ============================================================================

enum class PromotionTargetType {
    TAG,
    ITEM
}

data class PromotionTarget(
    val type: PromotionTargetType,
    val targetId: Long,
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
        val amount: BigDecimal
    ) : PromotionBenefit()

    data class BuyXGetYSameItem(
        val buyQuantity: Int,
        val rewardQuantity: Int,
        val repeat: Boolean
    ) : PromotionBenefit()
}

data class Promotion(
    val id: Long,
    val name: String,
    val active: Boolean,
    val priority: Int,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
    val schedule: PromotionSchedule,
    val targets: List<PromotionTarget>,
    val benefit: PromotionBenefit,
    val version: Long = 1L
)

// ============================================================================
// W I R E   D T O s  (Promotions)
// ============================================================================

data class PromotionResponseDto(
    val id: Long,
    val name: String,
    val active: Boolean,
    val priority: Int,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val schedule: PromotionScheduleDto,
    val targets: List<PromotionTargetDto>,
    val benefit: PromotionBenefitDto,
    val version: Long
)

data class PromotionScheduleDto(
    val daysOfWeek: Set<Int>,
    val allDay: Boolean,
    val startTime: String?,
    val endTime: String?
)

data class PromotionTargetDto(
    val type: String, // "TAG" or "ITEM"
    val targetId: Long,
    val displayName: String
)

data class PromotionBenefitDto(
    val type: String, // "FIXED_UNIT_PRICE" or "BUY_X_GET_Y_SAME_ITEM"
    val amount: BigDecimal? = null,
    val buyQuantity: Int? = null,
    val rewardQuantity: Int? = null,
    val repeat: Boolean? = null
)

data class PromotionCreateRequestDto(
    val name: String,
    val active: Boolean,
    val priority: Int,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val schedule: PromotionScheduleDto,
    val targets: List<PromotionTargetDto>,
    val benefit: PromotionBenefitDto
)

data class PromotionUpdateRequestDto(
    val name: String,
    val active: Boolean,
    val priority: Int,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val schedule: PromotionScheduleDto,
    val targets: List<PromotionTargetDto>,
    val benefit: PromotionBenefitDto,
    val version: Long
)

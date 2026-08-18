package com.restaurant.sushimei.frontend.data.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

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

    data class BuyXGetY(
        val type: String,
        val buyQuantity: Int,
        val rewardQuantity: Int,
        val repeat: Boolean
    ) : PromotionBenefit() {

        init {
            require(type == SAME_ITEM || type == ELIGIBLE_ITEM) {
                "Unsupported BuyXGetY type: $type. Expected $SAME_ITEM or $ELIGIBLE_ITEM."
            }
        }

        companion object {
            const val SAME_ITEM = "BUY_X_GET_Y_SAME_ITEM"
            const val ELIGIBLE_ITEM = "BUY_X_GET_Y_ELIGIBLE_ITEM"

            fun isEligibleItemVariant(type: String) =
                type == ELIGIBLE_ITEM

            fun validated(
                type: String,
                buyQuantity: Int,
                rewardQuantity: Int,
                repeat: Boolean
            ) = BuyXGetY(type, buyQuantity, rewardQuantity, repeat)
        }
    }
}

data class Promotion(
    val id: Long,
    val name: String,
    val active: Boolean,
    val priority: Int,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val schedule: PromotionSchedule,
    val targets: List<PromotionTarget>,
    val benefit: PromotionBenefit,
    val version: Long = 1L
)

// ============================================================================
// W I R E   D T O s  (Promotions)
// ============================================================================

data class PromotionResponse(
    val id: Long,
    val name: String,
    val active: Boolean,
    val priority: Int,
    val benefitType: String,
    val fixedUnitPrice: BigDecimal?,
    val buyQuantity: Int?,
    val rewardQuantity: Int?,
    val repeat: Boolean?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val daysOfWeek: Set<Int>,
    val targets: List<PromotionTargetResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long
)

data class PromotionTargetResponse(
    val targetType: String,
    val targetId: Long
)

data class PromotionTargetRequest(
    val targetType: String,
    val targetId: Long
)

data class PromotionCreateRequest(
    val name: String,
    val active: Boolean,
    val priority: Int,
    val benefitType: String,
    val fixedUnitPrice: BigDecimal?,
    val buyQuantity: Int?,
    val rewardQuantity: Int?,
    val repeat: Boolean?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val daysOfWeek: Set<Int>,
    val targets: List<PromotionTargetRequest>
)

data class PromotionUpdateRequest(
    val name: String,
    val active: Boolean,
    val priority: Int,
    val benefitType: String,
    val fixedUnitPrice: BigDecimal?,
    val buyQuantity: Int?,
    val rewardQuantity: Int?,
    val repeat: Boolean?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val daysOfWeek: Set<Int>,
    val targets: List<PromotionTargetRequest>,
    val version: Long
)

package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

class RemotePromotionRepository(
    private val api: SushiMeiApi
) : IPromotionRepository {

    private val promotionsFlow = MutableStateFlow<List<Promotion>>(emptyList())

    private fun PromotionResponse.toDomain() = Promotion(
        id = id,
        name = name,
        active = active,
        priority = priority,
        validFrom = validFrom,
        validUntil = validUntil,
        schedule = PromotionSchedule(
            daysOfWeek = daysOfWeek,
            allDay = true
        ),
        targets = targets.map {
            PromotionTarget(
                type = PromotionTargetType.valueOf(it.targetType),
                targetId = it.targetId,
                displayName = it.targetId.toString()
            )
        },
        benefit = when (benefitType) {
            "BUY_X_GET_Y_SAME_ITEM" -> PromotionBenefit.BuyXGetYSameItem(
                buyQuantity = requireNotNull(buyQuantity) { "buyQuantity is required for $benefitType" },
                rewardQuantity = requireNotNull(rewardQuantity) { "rewardQuantity is required for $benefitType" },
                repeat = requireNotNull(repeat) { "repeat is required for $benefitType" }
            )
            "FIXED_UNIT_PRICE" -> PromotionBenefit.FixedUnitPrice(
                amount = requireNotNull(fixedUnitPrice) { "fixedUnitPrice is required for $benefitType" }
            )
            else -> error("Unsupported promotion benefit type: $benefitType")
        },
        version = version
    )

    private fun Promotion.toCreateRequest() = PromotionCreateRequest(
        name = name,
        active = active,
        priority = priority,
        benefitType = when (benefit) {
            is PromotionBenefit.FixedUnitPrice -> "FIXED_UNIT_PRICE"
            is PromotionBenefit.BuyXGetYSameItem -> "BUY_X_GET_Y_SAME_ITEM"
        },
        fixedUnitPrice = (benefit as? PromotionBenefit.FixedUnitPrice)?.amount,
        buyQuantity = (benefit as? PromotionBenefit.BuyXGetYSameItem)?.buyQuantity,
        rewardQuantity = (benefit as? PromotionBenefit.BuyXGetYSameItem)?.rewardQuantity,
        repeat = (benefit as? PromotionBenefit.BuyXGetYSameItem)?.repeat,
        validFrom = validFrom,
        validUntil = validUntil,
        daysOfWeek = schedule.daysOfWeek,
        targets = targets.map {
            PromotionTargetRequest(
                targetType = it.type.name,
                targetId = it.targetId
            )
        }
    )

    private fun Promotion.toUpdateRequest() = PromotionUpdateRequest(
        name = name,
        active = active,
        priority = priority,
        benefitType = when (benefit) {
            is PromotionBenefit.FixedUnitPrice -> "FIXED_UNIT_PRICE"
            is PromotionBenefit.BuyXGetYSameItem -> "BUY_X_GET_Y_SAME_ITEM"
        },
        fixedUnitPrice = (benefit as? PromotionBenefit.FixedUnitPrice)?.amount,
        buyQuantity = (benefit as? PromotionBenefit.BuyXGetYSameItem)?.buyQuantity,
        rewardQuantity = (benefit as? PromotionBenefit.BuyXGetYSameItem)?.rewardQuantity,
        repeat = (benefit as? PromotionBenefit.BuyXGetYSameItem)?.repeat,
        validFrom = validFrom,
        validUntil = validUntil,
        daysOfWeek = schedule.daysOfWeek,
        targets = targets.map {
            PromotionTargetRequest(
                targetType = it.type.name,
                targetId = it.targetId
            )
        },
        version = version
    )


    override fun observePromotions(): Flow<List<Promotion>> = promotionsFlow.asStateFlow()

    override suspend fun getPromotions(): List<Promotion> {
        val response = api.getPromotions(includeInactive = true)
        if (response.isSuccessful) {
            val list = response.body()?.map { it.toDomain() } ?: emptyList()
            promotionsFlow.value = list
            return list
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getActivePromotions(): List<Promotion> {
        val response = api.getActivePromotions()
        if (response.isSuccessful) {
            val list = response.body()?.map { it.toDomain() } ?: emptyList()
            promotionsFlow.value = list
            return list
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getPromotion(id: Long): Promotion? {
        val response = api.getPromotion(id)
        if (response.isSuccessful) {
            return response.body()?.toDomain()
        } else if (response.code() == 404) {
            return null
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun createPromotion(promotion: Promotion): Promotion {
        val response = api.createPromotion(promotion.toCreateRequest())
        if (response.isSuccessful) {
            getPromotions()
            return response.body()?.toDomain() ?: throw Exception("Body null")
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun updatePromotion(promotion: Promotion): Promotion {
        val response = api.updatePromotion(promotion.id, promotion.toUpdateRequest())
        if (response.isSuccessful) {
            getPromotions()
            return response.body()?.toDomain() ?: throw Exception("Body null")
        } else if (response.code() == 409) {
            throw Exception("VERSION_CONFLICT")
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun archivePromotion(id: Long) {
        val response = api.deletePromotion(id)
        if (response.isSuccessful) {
            getPromotions()
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun quoteCart(cart: List<ConfiguredProduct>): OrderPricingPreview {
        // Build the quote request recursively
        fun mapGroups(groups: List<ConfiguredGroup>): List<QuoteRequestGroupDto> {
            return groups.map { group ->
                QuoteRequestGroupDto(
                    groupId = group.groupId,
                    selections = group.selections.map { sel ->
                        QuoteRequestSelectionDto(
                            menuItemId = sel.menuItemId,
                            quantity = sel.quantity,
                            groups = mapGroups(sel.groups)
                        )
                    }
                )
            }
        }

        val request = QuoteRequestDto(
            lines = cart.map {
                QuoteRequestLineDto(
                    lineKey = it.id,
                    menuItemId = it.menuItemId,
                    quantity = it.quantity,
                    groups = mapGroups(it.groups),
                    rewardConfigurations = it.promotionSelection?.rewardConfigurations?.map { reward ->
                        QuoteRequestRewardConfigDto(
                            rewardOrdinal = reward.rewardOrdinal,
                            groups = mapGroups(reward.groups)
                        )
                    } ?: emptyList()
                )
            }
        )

        val response = api.quotePromotions(request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw Exception("Quote response body null")

            val selectedPromotionByLine = cart.associate { it.id to it.promotionSelection }
            body.lines.forEach { line ->
                val selectedPromotion = selectedPromotionByLine[line.lineKey]
                if (selectedPromotion != null && line.appliedPromotion?.id != selectedPromotion.promotionId) {
                    throw Exception("La promoción ${selectedPromotion.promotionName} no está disponible para esta orden.")
                }
            }
            
            // Map the quote response back to OrderPricingPreview
            
            // Reconstruct the reward items conceptually (for UI)
            val allRewards = mutableListOf<ConfiguredProduct>()
            val adjustments = mutableListOf<PricingAdjustment>()

            body.lines.forEach { line ->
                // Collect line adjustments
                if (line.appliedPromotion != null) {
                    adjustments.add(
                        PricingAdjustment(
                            label = line.appliedPromotion.name,
                            amount = line.promotionAdjustmentTotal,
                            sourceType = "PROMOTION",
                            promotionId = line.appliedPromotion.id
                        )
                    )
                }

                // Map rewards to ConfiguredProduct physically separate units
                line.rewards.forEach { reward ->
                    allRewards.add(
                        ConfiguredProduct(
                            id = java.util.UUID.randomUUID().toString(), // local UI key
                            menuItemId = reward.menuItemId,
                            name = "${reward.name} (Promo: ${reward.promotion.name})",
                            quantity = 1,
                            baseUnitPrice = BigDecimal.ZERO, // Rewards are technically free base price
                            groups = emptyList(), // Can be mapped if reward.configuration is present
                            unitTotal = BigDecimal.ZERO,
                            total = BigDecimal.ZERO
                        )
                    )
                }
            }

            return OrderPricingPreview(
                subtotal = body.catalogBaseSubtotal + body.configurationAdjustmentTotal,
                adjustments = adjustments,
                rewardItems = allRewards,
                total = body.total
            )

        } else {
            throw Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}")
        }
    }
}

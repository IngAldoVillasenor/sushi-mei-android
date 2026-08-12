package com.restaurant.sushimei.frontend.data.model

import java.math.BigDecimal
import java.time.Instant

// ============================================================================

// E R R O R   D T O s

// ============================================================================

data class ApiErrorDto(
    val code: String,
    val message: String
)

// ============================================================================

// C A T A L O G   D T O s

// ============================================================================

data class CatalogTagSummary(
    val id: Long,
    val code: String,
    val name: String,
    val active: Boolean,
    val displayOrder: Int
)

data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val category: String,
    val price: BigDecimal,
    val active: Boolean,
    val available: Boolean,
    val standaloneOrderable: Boolean,
    val displayOrder: Int,
    val tags: List<CatalogTagSummary>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class MenuItemCreateRequestDto(
    val name: String,
    val description: String?,
    val category: String,
    val price: BigDecimal,
    val available: Boolean,
    val standaloneOrderable: Boolean,
    val displayOrder: Int
)

data class MenuItemUpdateRequestDto(
    val name: String,
    val description: String?,
    val category: String,
    val price: BigDecimal,
    val active: Boolean,
    val available: Boolean,
    val standaloneOrderable: Boolean,
    val displayOrder: Int,
    val version: Long
)

// ----------------------------------------------------------------------------

// Tags API

// ----------------------------------------------------------------------------

data class CatalogTagDto(
    val id: Long,
    val code: String,
    val name: String,
    val active: Boolean,
    val displayOrder: Int,
    val version: Long
)

data class TagCreateRequestDto(
    val code: String,
    val name: String,
    val displayOrder: Int
)

data class TagUpdateRequestDto(
    val name: String,
    val active: Boolean,
    val displayOrder: Int,
    val version: Long
)

data class ItemTagsUpdateRequestDto(
    val itemVersion: Long,
    val tagIds: List<Long>
)

// ----------------------------------------------------------------------------

// Configuration Operational APIs

// ----------------------------------------------------------------------------

data class ConfigurationResponseDto(
    val menuItemId: Long,
    val name: String,
    val standaloneOrderable: Boolean,
    val basePrice: BigDecimal,
    val requiresConfiguration: Boolean,
    val groups: List<ConfigurationGroupDto> = emptyList()
)

data class ConfigurationGroupDto(
    val id: Long,
    val name: String,
    val minSelections: Int,
    val maxSelections: Int,
    val allowDuplicates: Boolean,
    val options: List<ConfigurationOptionDto> = emptyList()
)

data class ConfigurationOptionDto(
    val menuItemId: Long,
    val name: String,
    val category: String,
    val catalogPrice: BigDecimal,
    val available: Boolean,
    val requiresConfiguration: Boolean,
    val priceAdjustment: BigDecimal
)

// ----------------------------------------------------------------------------

// Quote APIs (Phase 6A2 - Phase 6A3)

// ----------------------------------------------------------------------------

data class QuoteRequestDto(
    val lines: List<QuoteRequestLineDto> = emptyList()
)

data class QuoteRequestLineDto(
    val lineKey: String,
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList(),
    val rewardConfigurations: List<QuoteRequestRewardConfigDto> = emptyList()
)

data class QuoteRequestGroupDto(
    val groupId: Long,
    val selections: List<QuoteRequestSelectionDto> = emptyList()
)

data class QuoteRequestSelectionDto(
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList()
)

data class QuoteRequestRewardConfigDto(
    val rewardOrdinal: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList()
)

data class QuoteResponseDto(
    val quotedAt: Instant,
    val businessTimeZone: String,
    val lines: List<QuoteResponseLineDto> = emptyList(),
    val catalogBaseSubtotal: BigDecimal,
    val configurationAdjustmentTotal: BigDecimal,
    val promotionAdjustmentTotal: BigDecimal,
    val total: BigDecimal
)

data class QuoteResponseLineDto(
    val lineKey: String,
    val menuItemId: Long,
    val name: String,
    val quantity: Int,
    val catalogBaseUnitPrice: BigDecimal,
    val chargedBaseUnitPrice: BigDecimal,
    val configuration: ItemQuoteResponseDto,
    val appliedPromotion: PromotionSummaryDto?,
    val promotionAdjustmentTotal: BigDecimal,
    val rewards: List<QuoteResponseRewardDto> = emptyList(),
    val lineTotal: BigDecimal
)

data class QuoteResponseGroupDto(
    val groupId: Long,
    val name: String,
    val selections: List<QuoteResponseSelectionDto> = emptyList()
)

data class QuoteResponseSelectionDto(
    val menuItemId: Long,
    val name: String,
    val quantity: Int,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal,
    val groups: List<QuoteResponseGroupDto> = emptyList()
)

data class QuoteResponseRewardDto(
    val sourceLineKey: String,
    val rewardOrdinal: Int,
    val promotion: PromotionSummaryDto,
    val menuItemId: Long,
    val name: String,
    val catalogBaseUnitPrice: BigDecimal,
    val chargedBaseUnitPrice: BigDecimal,
    val configuration: ItemQuoteResponseDto,
    val configurationAdjustmentTotal: BigDecimal,
    val total: BigDecimal
)

data class PromotionSummaryDto(
    val id: Long,
    val name: String
)

// ----------------------------------------------------------------------------

// Item Quote APIs (Phase 6A2)

// ----------------------------------------------------------------------------

data class ItemQuoteRequestDto(
    val quantity: Int,
    val groups: List<ItemQuoteRequestGroupDto> = emptyList()
)

data class ItemQuoteRequestGroupDto(
    val groupId: Long,
    val selections: List<ItemQuoteRequestSelectionDto> = emptyList()
)

data class ItemQuoteRequestSelectionDto(
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<ItemQuoteRequestGroupDto> = emptyList()
)

data class ItemQuoteResponseDto(
    val menuItemId: Long,
    val name: String,
    val quantity: Int,
    val baseUnitPrice: BigDecimal,
    val baseTotal: BigDecimal,
    val groups: List<ItemQuoteResponseGroupDto> = emptyList(),
    val unitAdjustmentTotal: BigDecimal,
    val unitTotal: BigDecimal,
    val total: BigDecimal
)

data class ItemQuoteResponseGroupDto(
    val groupId: Long,
    val name: String,
    val selections: List<ItemQuoteResponseSelectionDto> = emptyList()
)

data class ItemQuoteResponseSelectionDto(
    val menuItemId: Long,
    val name: String,
    val quantity: Int,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal,
    val groups: List<ItemQuoteResponseGroupDto> = emptyList()
)

// ============================================================================

// O R D E R S   A P I   D T O s (Phase 6B)

// ============================================================================

enum class FulfillmentType { PICKUP, DELIVERY }

enum class PaymentMethod { CASH, TRANSFER, CARD }

enum class OrderResult { CREATED, ALREADY_CREATED }

data class ManualPosOrderRequest(
    val requestId: String,
    val fulfillmentType: FulfillmentType,
    val paymentMethod: PaymentMethod,
    val deliveryAddress: String?,
    val pickupName: String?,
    val cashDenomination: BigDecimal?,
    val lines: List<PosOrderRequestLineDto>
)

data class PosOrderRequestLineDto(
    val lineKey: String,
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList(),
    val rewardConfigurations: List<QuoteRequestRewardConfigDto> = emptyList()
)

data class ManualPosOrderResponse(
    val id: Long,
    val requestId: String,
    val result: OrderResult,
    val orderSource: String,
    val createdByUserId: Long?,
    val fulfillmentType: FulfillmentType,
    val paymentMethod: PaymentMethod,
    val deliveryAddress: String?,
    val pickupName: String?,
    val cashDenomination: BigDecimal?,
    val status: String,
    val createdAt: Instant,
    val lines: List<PosOrderResponseLineDto>,
    val total: BigDecimal
)

data class PosOrderResponseLineDto(
    val id: Long,
    val lineKind: String,
    val lineKey: String?,
    val sourceMenuItemId: Long,
    val name: String,
    val quantity: Int,
    val catalogBaseUnitPrice: BigDecimal,
    val chargedBaseUnitPrice: BigDecimal,
    val configurationAdjustmentAmount: BigDecimal,
    val finalUnitAmount: BigDecimal,
    val finalLineTotal: BigDecimal,
    val promotion: OrderPromotionSnapshotDto?,
    val rewardOrdinal: Int?,
    val configuration: List<OrderConfigurationSnapshotDto> = emptyList(),
    val rewards: List<PosOrderResponseLineDto> = emptyList()
)

data class OrderConfigurationSnapshotDto(
    val id: Long,
    val parentSelectionSnapshotId: Long?,
    val groupId: Long,
    val groupName: String,
    val selectionPosition: Int,
    val menuItemId: Long,
    val itemName: String,
    val quantity: Int,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal
)

data class OrderPromotionSnapshotDto(
    val id: Long,
    val name: String,
    val benefitType: String
)

data class OperationalOrderSummaryDto(
    val id: Long,
    val orderSource: String?,
    val status: String,
    val fulfillmentType: FulfillmentType?,
    val paymentMethod: PaymentMethod?,
    val deliveryAddress: String?,
    val pickupName: String?,
    val cashDenomination: BigDecimal?,
    val phoneNumber: String?,
    val total: BigDecimal?,
    val createdAt: Instant?,
    val requiresPaymentValidation: Boolean,
    val structuredLinesAvailable: Boolean
)

data class OperationalOrderDetailDto(
    val id: Long,
    val requestId: String?,
    val orderSource: String?,
    val createdByUserId: Long?,
    val fulfillmentType: FulfillmentType?,
    val paymentMethod: PaymentMethod?,
    val deliveryAddress: String?,
    val pickupName: String?,
    val cashDenomination: BigDecimal?,
    val phoneNumber: String?,
    val transferReceiptPath: String?,
    val paymentNotes: String?,
    val status: String,
    val createdAt: Instant?,
    val total: BigDecimal?,
    val legacyOrderDetails: String?,
    val lines: List<OperationalOrderLineDto>
)

data class OperationalOrderLineDto(
    val id: Long,
    val lineKind: String,
    val lineKey: String?,
    val sourceMenuItemId: Long?,
    val name: String,
    val quantity: Int,
    val catalogBaseUnitPrice: BigDecimal?,
    val chargedBaseUnitPrice: BigDecimal?,
    val configurationAdjustmentAmount: BigDecimal?,
    val finalUnitAmount: BigDecimal,
    val finalLineTotal: BigDecimal,
    val promotion: OrderPromotionSnapshotDto?,
    val rewardOrdinal: Int?,
    val sourcePaidLineId: Long?,
    val configuration: List<OrderConfigurationSnapshotDto> = emptyList()
)

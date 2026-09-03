package com.restaurant.sushimei.frontend.data.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// ============================================================================

// E R R O R   D T O s

// ============================================================================

data class ApiErrorDto(
    val code: String?,
    val message: String?
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

enum class ItemPricingMode {
    BASE_PLUS_ADJUSTMENTS,
    SELECTION_SUM
}

data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val category: String,
    val price: BigDecimal,
    val active: Boolean,
    val available: Boolean,
    val standaloneOrderable: Boolean,
    val requiresConfiguration: Boolean?,
    val pricingMode: ItemPricingMode?,
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
    val rewardConfigurations: List<QuoteRequestRewardConfigDto> = emptyList(),
    val omittedComponentIds: List<Long> = emptyList(),
    val note: String? = null
)

data class QuoteRequestGroupDto(
    val groupId: Long,
    val selections: List<QuoteRequestSelectionDto> = emptyList()
)

data class QuoteRequestSelectionDto(
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList(),
    val omittedComponentIds: List<Long> = emptyList(),
    val note: String? = null
)

data class QuoteRequestRewardConfigDto(
    val rewardOrdinal: Int,
    val menuItemId: Long? = null,
    val groups: List<QuoteRequestGroupDto> = emptyList(),
    val omittedComponentIds: List<Long> = emptyList(),
    val note: String? = null
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
    val groups: List<QuoteResponseGroupDto> = emptyList(),    val omittedComponents: List<DefaultComponentResponse> = emptyList(),
    val note: String? = null
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
    val configurationAdjustmentTotal: BigDecimal,    val total: BigDecimal,
    val omittedComponents: List<DefaultComponentResponse> = emptyList(),
    val note: String? = null
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
    val groups: List<ItemQuoteRequestGroupDto> = emptyList(),
    val omittedComponentIds: List<Long> = emptyList(),
    val note: String? = null
)

data class ItemQuoteRequestGroupDto(
    val groupId: Long,
    val selections: List<ItemQuoteRequestSelectionDto> = emptyList()
)

data class ItemQuoteRequestSelectionDto(
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<ItemQuoteRequestGroupDto> = emptyList(),
    val omittedComponentIds: List<Long> = emptyList(),
    val note: String? = null
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
    val displayOnTicket: Boolean = true,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal,
    val groups: List<ItemQuoteResponseGroupDto> = emptyList(),    val omittedComponents: List<DefaultComponentResponse> = emptyList(),
    val note: String? = null
)

// ============================================================================

// O R D E R S   A P I   D T O s (Phase 6B)

// ============================================================================

enum class FulfillmentType { PICKUP, DELIVERY }

enum class OrderPaymentTiming { IMMEDIATE, ON_DELIVERY }

enum class PaymentMethod { CASH, TRANSFER, CARD }

enum class OrderResult { CREATED, ALREADY_CREATED }

data class OrderPaymentCollectionRequest(
    val paymentMethod: PaymentMethod,
    val cashDenomination: BigDecimal?
)

data class OrderPaymentCollectionResponse(
    val orderId: Long,
    val previousStatus: String,
    val currentStatus: String,
    val paymentTiming: OrderPaymentTiming,
    val paymentMethod: PaymentMethod,
    val cashDenomination: BigDecimal?,
    val paymentCollectedAt: Instant?,
    val paymentCollectedByUserId: Long?
)

data class ManualPricedLineRequest(
    val lineKey: String,
    val description: String,
    val quantity: Int,
    val unitAmount: BigDecimal
)

data class ManualPosOrderRequest(
    val requestId: String,
    val fulfillmentType: FulfillmentType,
    val paymentMethod: PaymentMethod?,
    val paymentTiming: OrderPaymentTiming? = null,
    val deliveryAddress: String?,
    val pickupName: String?,
    val cashDenomination: BigDecimal?,
    val lines: List<PosOrderRequestLineDto>,
    val manualLines: List<ManualPricedLineRequest> = emptyList()
)

data class PosOrderRequestLineDto(
    val lineKey: String,
    val menuItemId: Long,
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList(),
    val rewardConfigurations: List<QuoteRequestRewardConfigDto> = emptyList(),
    val omittedComponentIds: List<Long> = emptyList(),
    val note: String? = null
)

data class ManualPosOrderResponse(
    val id: Long,
    val requestId: String,
    val result: OrderResult,
    val orderSource: String,
    val createdByUserId: Long?,
    val fulfillmentType: FulfillmentType,
    val paymentMethod: PaymentMethod?,
    val paymentTiming: OrderPaymentTiming = OrderPaymentTiming.IMMEDIATE,
    val requiresPaymentCollection: Boolean = false,
    val paymentCollectedAt: Instant? = null,
    val paymentCollectedByUserId: Long? = null,
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
    val sourceMenuItemId: Long?,
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
    val omittedComponents: List<OrderComponentOmissionSnapshotDto> = emptyList(),
    val note: String? = null,
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
    val displayOnTicket: Boolean = true,
    val quantity: Int,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal,
    val omittedComponents: List<OrderComponentOmissionSnapshotDto> = emptyList(),
    val note: String? = null
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
    val paymentTiming: OrderPaymentTiming = OrderPaymentTiming.IMMEDIATE,
    val requiresPaymentCollection: Boolean = false,
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
    val paymentTiming: OrderPaymentTiming = OrderPaymentTiming.IMMEDIATE,
    val requiresPaymentCollection: Boolean = false,
    val paymentCollectedAt: Instant? = null,
    val paymentCollectedByUserId: Long? = null,
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
    val configuration: List<OrderConfigurationSnapshotDto> = emptyList(),
    val omittedComponents: List<OrderComponentOmissionSnapshotDto> = emptyList(),
    val note: String? = null
)


data class HistoricalOrderSummaryDto(
    val id: Long,
    val externalOrderId: String?,
    val externalReference: String?,
    val orderSource: String?,
    val status: String,
    val fulfillmentType: String?,
    val paymentMethod: String?,
    val paymentTiming: String? = null,
    val requiresPaymentCollection: Boolean = false,
    val pickupName: String?,
    val total: BigDecimal?,
    val createdAt: Instant?,
    val structuredLinesAvailable: Boolean
)

data class HistoricalOrdersPageDto(
    val content: List<HistoricalOrderSummaryDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)


data class SalesBySourceResponse(
    val source: String?, // Important: Nullable!
    val completedOrderCount: Long,
    val completedRevenue: java.math.BigDecimal
)

data class HistoricalAnalyticsResponse(
    val from: String,
    val to: String,
    val completedRevenue: java.math.BigDecimal,
    val completedOrderCount: Long,
    val averageCompletedTicket: java.math.BigDecimal,
    val voidedOrderCount: Long,
    val salesBySource: List<SalesBySourceResponse>
)


// ============================================================================
// BUSINESS DAY (Phase 8F)
// ============================================================================

enum class BusinessDayStatus { OPEN, CLOSED }

data class BusinessDayResponse(
    val businessDayId: Long,
    val businessDate: String,
    val status: BusinessDayStatus,
    val openingCashAmount: java.math.BigDecimal,
    val openedAt: java.time.Instant,
    val openedByUserId: Long,
    val closedAt: java.time.Instant?,
    val closedByUserId: Long?,
    val completedSalesAmount: java.math.BigDecimal,
    val cashSalesAmount: java.math.BigDecimal,
    val transferSalesAmount: java.math.BigDecimal,
    val cardSalesAmount: java.math.BigDecimal,
    val unclassifiedSalesAmount: java.math.BigDecimal,
    val completedOrderCount: Long,
    val voidedOrderCount: Long,
    val expectedClosingCashAmount: java.math.BigDecimal,
    val actualClosingCashAmount: java.math.BigDecimal?,
    val cashDifferenceAmount: java.math.BigDecimal?,
    val closureId: Long? = null,
    val closureNumber: Int? = null,
    val cashExpenseAmount: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val cashExpenseCount: Long = 0
)

data class OpenBusinessDayRequest(
    val openingCashAmount: java.math.BigDecimal
)

data class CloseBusinessDayRequest(
    val actualClosingCashAmount: java.math.BigDecimal
)


// ============================================================================
// PHASE MVP-2: Generic Customization & Open Sale
// ============================================================================

data class DefaultComponentResponse(
    val id: Long,
    val code: String,
    val displayName: String,
    val detail: String?,
    val includedByDefault: Boolean,
    val removable: Boolean,
    val displayOrder: Int,
    val active: Boolean
)

data class OpenSaleRequest(
    val requestId: String,
    val description: String,
    val amount: BigDecimal,
    val paymentMethod: PaymentMethod,
    val cashDenomination: BigDecimal?
)

data class OpenSaleResponse(
    val id: Long,
    val requestId: String,
    val result: String,
    val orderSource: String,
    val createdByUserId: Long,
    val description: String,
    val quantity: Int,
    val unitAmount: BigDecimal,
    val total: BigDecimal,
    val paymentMethod: PaymentMethod,
    val cashDenomination: BigDecimal?,
    val status: String,
    val createdAt: String
)

data class OrderComponentOmissionSnapshotDto(
    val id: Long,
    val sourceComponentId: Long,
    val code: String,
    val displayName: String,
    val detail: String?,
    val displayOrder: Int
)


// ============================================================================
// ============================================================================
// FASE 6A2: Configuration Definition (Admin)
// ============================================================================

enum class SelectionRuleTargetType {
    ITEM,
    TAG
}

enum class PricingPolicy {
    INCLUDED,
    PRICE_DIFFERENCE,
    FULL_ITEM_PRICE,
    FIXED_SURCHARGE
}

data class MenuSelectionRuleResponse(
    val id: Long,
    val selectionGroupId: Long,
    val targetType: SelectionRuleTargetType,
    val targetId: Long,
    val pricingPolicy: PricingPolicy,
    val referencePrice: BigDecimal?,
    val fixedSurcharge: BigDecimal?,
    val priority: Int,
    val active: Boolean,
    val version: Long,
    val createdAt: java.time.Instant?,
    val updatedAt: java.time.Instant?
)

data class MenuSelectionGroupResponse(
    val id: Long,
    val parentMenuItemId: Long,
    val name: String,
    val minSelections: Int,
    val maxSelections: Int,
    val allowDuplicates: Boolean,
    val displayOrder: Int,
    val active: Boolean,
    val version: Long,
    val createdAt: java.time.Instant?,
    val updatedAt: java.time.Instant?
)

data class MenuSelectionGroupDefinitionResponse(
    val group: MenuSelectionGroupResponse,
    val rules: List<MenuSelectionRuleResponse>
)



data class MenuItemConfigurationDefinitionResponse(
    val menuItemId: Long,
    val name: String,
    val version: Long,
    val tags: List<CatalogTagSummary>,
    val groups: List<MenuSelectionGroupDefinitionResponse>
)

data class CreateMenuSelectionGroupRequest(
    val name: String,
    val minSelections: Int,
    val maxSelections: Int,
    val allowDuplicates: Boolean,
    val displayOrder: Int
)

data class UpdateMenuSelectionGroupRequest(
    val name: String,
    val minSelections: Int,
    val maxSelections: Int,
    val allowDuplicates: Boolean,
    val displayOrder: Int,
    val active: Boolean,
    val version: Long
)

data class CreateMenuSelectionRuleRequest(
    val targetType: SelectionRuleTargetType,
    val targetId: Long,
    val pricingPolicy: PricingPolicy,
    val referencePrice: BigDecimal?,
    val fixedSurcharge: BigDecimal?,
    val priority: Int
)

data class UpdateMenuSelectionRuleRequest(
    val targetType: SelectionRuleTargetType,
    val targetId: Long,
    val pricingPolicy: PricingPolicy,
    val referencePrice: BigDecimal?,
    val fixedSurcharge: BigDecimal?,
    val priority: Int,
    val active: Boolean,
    val version: Long
)


// ============================================================================
// POS ORDER VOID (feat/pos-order-cancellation-ui)
// ============================================================================

data class VoidOrderRequest(
    val reason: String
)

data class VoidOrderResponse(
    val orderId: Long,
    val previousStatus: String,
    val currentStatus: String,
    val voidReason: String,
    val voidedAt: java.time.Instant,
    val voidedByUserId: Long
)



    // Convert DTO to Domain
    fun MenuItemResponse.toDomain() = MenuItem(
        id = id,
        nombre = name,
        categoria = category,
        precio = price,
        descripcion = description ?: "",
        emoji = "🍣", // Fallback emoji for remote items without specific emoji field
        activo = active,
        standaloneOrderable = standaloneOrderable,
        requiresConfiguration = requireNotNull(requiresConfiguration) { "requiresConfiguration must not be omitted by the server" },
                pricingMode = requireNotNull(pricingMode) { "pricingMode must not be omitted by the server" },
        tags = tags,
        available = available,
        displayOrder = displayOrder,
        version = version
    )


data class CashExpenseRequest(
    val requestId: UUID,
    val amount: BigDecimal,
    val description: String,
    val note: String?
)

enum class CashExpenseResult {
    CREATED, ALREADY_CREATED
}

data class CashExpenseDto(
    val id: Long,
    val businessDayId: Long,
    val requestId: UUID,
    val amount: BigDecimal,
    val description: String,
    val note: String?,
    val createdAt: Instant,
    val createdByUserId: Long
)

data class CashExpenseCreateResponse(
    val expense: CashExpenseDto,
    val result: CashExpenseResult
)

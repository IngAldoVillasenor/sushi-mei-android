package com.restaurant.sushimei.frontend.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

// ============================================================================
// W I R E   D T O s
// ============================================================================

data class CatalogItemDto(
    val id: String,
    val name: String,
    val description: String?,
    val category: String,
    val price: BigDecimal,
    val active: Boolean,
    val available: Boolean,
    val displayOrder: Int,
    val standaloneOrderable: Boolean,
    val tags: List<String>?,
    val version: Long
)

// ----------------------------------------------------------------------------
// Configuration Operational APIs
// ----------------------------------------------------------------------------

data class ConfigurationResponseDto(
    val menuItemId: String,
    val name: String,
    val standaloneOrderable: Boolean,
    val basePrice: BigDecimal,
    val requiresConfiguration: Boolean,
    val groups: List<ConfigurationGroupDto> = emptyList()
)

data class ConfigurationGroupDto(
    val id: Int,
    val name: String,
    val minSelections: Int,
    val maxSelections: Int,
    val allowDuplicates: Boolean,
    val options: List<ConfigurationOptionDto> = emptyList()
)

data class ConfigurationOptionDto(
    val menuItemId: String,
    val name: String,
    val category: String,
    val catalogPrice: BigDecimal,
    val available: Boolean,
    val requiresConfiguration: Boolean,
    val priceAdjustment: BigDecimal
)

// ----------------------------------------------------------------------------
// Quote APIs
// ----------------------------------------------------------------------------

data class QuoteRequestDto(
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList()
)

data class QuoteRequestGroupDto(
    val groupId: Int,
    val selections: List<QuoteRequestSelectionDto> = emptyList()
)

data class QuoteRequestSelectionDto(
    val menuItemId: String,
    val quantity: Int,
    val groups: List<QuoteRequestGroupDto> = emptyList()
)

data class QuoteResponseDto(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val baseUnitPrice: BigDecimal,
    val baseTotal: BigDecimal,
    val groups: List<QuoteResponseGroupDto> = emptyList(),
    val unitAdjustmentTotal: BigDecimal,
    val unitTotal: BigDecimal,
    val total: BigDecimal
)

data class QuoteResponseGroupDto(
    val groupId: Int,
    val name: String,
    val selections: List<QuoteResponseSelectionDto> = emptyList()
)

data class QuoteResponseSelectionDto(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal,
    val groups: List<QuoteResponseGroupDto> = emptyList()
)

// ----------------------------------------------------------------------------
// Tags API
// ----------------------------------------------------------------------------

data class CatalogTagDto(
    val id: String,
    val code: String,
    val name: String,
    val active: Boolean,
    val displayOrder: Int,
    val version: Long
)

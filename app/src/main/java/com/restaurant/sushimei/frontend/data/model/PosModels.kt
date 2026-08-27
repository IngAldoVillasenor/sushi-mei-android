package com.restaurant.sushimei.frontend.data.model

import java.math.BigDecimal

data class MenuItem(
    val id: Long,
    val nombre: String,
    val categoria: String,
    val precio: BigDecimal,
    val descripcion: String = "",
    val emoji: String = "🍣",
    val activo: Boolean = true,
    val standaloneOrderable: Boolean = true,
    val requiresConfiguration: Boolean = false,
    val pricingMode: ItemPricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
    val tags: List<CatalogTagSummary> = emptyList()
)

/**
 * Modelo de dominio para un producto configurado (raíz).
 * Reemplaza al antiguo ConfiguredProduct plano.
 */
data class ConfiguredProduct(
    val id: String = java.util.UUID.randomUUID().toString(), // ID único de instancia (para UI)
    val menuItemId: Long,
    val name: String,
    val quantity: Int,
    val baseUnitPrice: BigDecimal,
    val groups: List<ConfiguredGroup> = emptyList(),
    val omittedComponents: List<DefaultComponentResponse> = emptyList(),
    val note: String? = null,

    // El precio total ajustado devuelto por la cotización del backend
    val unitTotal: BigDecimal = baseUnitPrice,
    val total: BigDecimal = baseUnitPrice * BigDecimal(quantity),
    val promotionSelection: PromotionLineSelection? = null
)

/**
 * Metadatos locales de una promoción elegida desde el POS. El ID y el nombre no
 * se envían como autoridad al backend; el servidor vuelve a resolver la regla.
 */
data class PromotionLineSelection(
    val promotionId: Long,
    val promotionName: String,
    val rewardConfigurations: List<ConfiguredRewardConfiguration> = emptyList()
)

/** Configuración independiente de una unidad gratuita de la misma línea. */
data class ConfiguredRewardConfiguration(
    val rewardOrdinal: Int,
    val menuItemId: Long? = null,
    val groups: List<ConfiguredGroup> = emptyList()
)

/**
 * Un grupo de configuración seleccionado dentro de un producto.
 */
data class ConfiguredGroup(
    val groupId: Long,
    val name: String,
    val selections: List<ConfiguredSelection>
)

/**
 * Una opción seleccionada dentro de un grupo (que recursivamente puede tener más grupos).
 */
data class ConfiguredSelection(
    val menuItemId: Long,
    val name: String,
    val quantity: Int,
    val catalogUnitPrice: BigDecimal,
    val priceAdjustment: BigDecimal,
    val groups: List<ConfiguredGroup> = emptyList()
)

/**
 * Reward item returned by the backend quote, associated to a purchased cart line
 * via [sourceLineKey]. All price fields come from the server — no local calculation.
 */
data class QuotedRewardItem(
    val sourceLineKey: String,             // matches ConfiguredProduct.id of the purchased line
    val rewardOrdinal: Int,
    val menuItemId: Long,
    val name: String,
    val promotionName: String,
    val catalogBaseUnitPrice: BigDecimal,
    val chargedBaseUnitPrice: BigDecimal,  // backend-authoritative; typically $0 for free rewards
    val configurationAdjustmentTotal: BigDecimal,
    val total: BigDecimal
)

data class QuotedCartLine(
    val lineKey: String,
    val menuItemId: Long,
    val chargedBaseUnitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

/**
 * Representa la pre-visualización de precios del carrito entero,
 * devuelta por el backend al aplicar promociones.
 */
data class OrderPricingPreview(
    val subtotal: BigDecimal,
    val adjustments: List<PricingAdjustment> = emptyList(),
    val quotedLines: List<QuotedCartLine> = emptyList(),
    val rewardItems: List<QuotedRewardItem> = emptyList(),
    val total: BigDecimal
)

/**
 * Un ajuste de precio aplicado al carrito (ej. una promoción).
 */
data class PricingAdjustment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val amount: BigDecimal, // Negativo para descuentos
    val sourceType: String = "PROMOTION",
    val promotionId: Long? = null
)

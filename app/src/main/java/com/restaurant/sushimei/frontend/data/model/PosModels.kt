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
    
    // El precio total ajustado devuelto por la cotización del backend
    val unitTotal: BigDecimal = baseUnitPrice, 
    val total: BigDecimal = baseUnitPrice * BigDecimal(quantity)
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
 * Representa la pre-visualización de precios del carrito entero, 
 * devuelta por el backend al aplicar promociones.
 */
data class OrderPricingPreview(
    val subtotal: BigDecimal,
    val adjustments: List<PricingAdjustment> = emptyList(),
    val rewardItems: List<ConfiguredProduct> = emptyList(),
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

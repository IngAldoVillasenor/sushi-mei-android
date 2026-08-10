package com.restaurant.sushimei.frontend.data.model

data class MenuItem(
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double,
    val descripcion: String = "",
    val emoji: String = "🍣",
    // Nuevos campos de configuración Phase 6A2
    val standaloneOrderable: Boolean = true,
    val tags: List<String> = emptyList()
)

/**
 * Modelo de dominio para un producto configurado (raíz).
 * Reemplaza al antiguo ConfiguredProduct plano.
 */
data class ConfiguredProduct(
    val id: String = java.util.UUID.randomUUID().toString(), // ID único de instancia (para UI)
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val baseUnitPrice: Double,
    val groups: List<ConfiguredGroup> = emptyList(),
    
    // El precio total ajustado devuelto por la cotización del backend
    val unitTotal: Double = baseUnitPrice, 
    val total: Double = baseUnitPrice * quantity
)

/**
 * Un grupo de configuración seleccionado dentro de un producto.
 */
data class ConfiguredGroup(
    val groupId: Int,
    val name: String,
    val selections: List<ConfiguredSelection>
)

/**
 * Una opción seleccionada dentro de un grupo (que recursivamente puede tener más grupos).
 */
data class ConfiguredSelection(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val catalogUnitPrice: Double,
    val priceAdjustment: Double,
    val groups: List<ConfiguredGroup> = emptyList()
)

/**
 * Representa la pre-visualización de precios del carrito entero, 
 * devuelta por el backend al aplicar promociones.
 */
data class OrderPricingPreview(
    val subtotal: Double,
    val adjustments: List<PricingAdjustment> = emptyList(),
    val total: Double
)

/**
 * Un ajuste de precio aplicado al carrito (ej. una promoción).
 */
data class PricingAdjustment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val amount: Double, // Negativo para descuentos
    val sourceType: String = "PROMOTION",
    val promotionId: String? = null
)

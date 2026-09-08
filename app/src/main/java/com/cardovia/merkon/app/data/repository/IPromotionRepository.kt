package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.model.ConfiguredProduct
import com.cardovia.merkon.app.data.model.OrderPricingPreview
import com.cardovia.merkon.app.data.model.Promotion
import kotlinx.coroutines.flow.Flow

interface IPromotionRepository {
    fun observePromotions(): Flow<List<Promotion>>
    
    suspend fun getPromotions(): List<Promotion>

    suspend fun getActivePromotions(): List<Promotion>
    
    suspend fun getPromotion(id: Long): Promotion?
    
    suspend fun createPromotion(promotion: Promotion): Promotion
    
    suspend fun updatePromotion(promotion: Promotion): Promotion
    
    suspend fun archivePromotion(id: Long)
    
    /**
     * Evalúa el carrito de compras contra las promociones activas en el backend.
     * Retorna el precio base total, los ajustes promocionales (descuentos) y el precio final.
     * Android NO ejecuta la lógica de promociones (Phase 6A3 Rule).
     */
    suspend fun quoteCart(cart: List<ConfiguredProduct>): OrderPricingPreview
}

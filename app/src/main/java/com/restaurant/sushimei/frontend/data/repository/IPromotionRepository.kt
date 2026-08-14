package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.OrderPricingPreview
import com.restaurant.sushimei.frontend.data.model.Promotion
import kotlinx.coroutines.flow.Flow

interface IPromotionRepository {
    fun observePromotions(): Flow<List<Promotion>>
    
    suspend fun getPromotions(): List<Promotion>
    
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

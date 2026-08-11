package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay

class MockPromotionRepository : IPromotionRepository {

    private val promotionsFlow = MutableStateFlow<List<Promotion>>(emptyList())

    init {
        // Inicializar con los fixtures requeridos (Lunes $69 y Jueves 2x1)
        val mondayPromotion = Promotion(
            id = 100L,
            name = "Lunes de clásicos $69",
            active = true,
            priority = 100,
            schedule = PromotionSchedule(
                daysOfWeek = setOf(1), // Lunes
                allDay = true
            ),
            targets = listOf(
                PromotionTarget(
                    type = PromotionTargetType.TAG,
                    targetId = 1L, // ROLL_CLASSIC
                    displayName = "Rollos clásicos"
                )
            ),
            benefit = PromotionBenefit.FixedUnitPrice(java.math.BigDecimal("69.00")),
            version = 1L
        )

        val thursdayPromotion = Promotion(
            id = 200L,
            name = "Martes 2x1 en Rollos Clásicos",
            active = true,
            priority = 10, // Menor número = mayor prioridad
            schedule = PromotionSchedule(
                daysOfWeek = setOf(2), // 1=Lunes, 2=Martes
                allDay = true
            ),
            targets = listOf(
                PromotionTarget(
                    type = PromotionTargetType.TAG,
                    targetId = 1L, // Assuming 1L is ROLL_CLASSIC
                    displayName = "Rollos Clásicos"
                )
            ),
            benefit = PromotionBenefit.BuyXGetYSameItem(
                buyQuantity = 2,
                rewardQuantity = 1,
                repeat = true
            ),
            version = 1L
        )

        promotionsFlow.value = listOf(mondayPromotion, thursdayPromotion)
    }

    override fun observePromotions(): Flow<List<Promotion>> = promotionsFlow.asStateFlow()

    override suspend fun getPromotions(): List<Promotion> {
        delay(300) // Simular red
        return promotionsFlow.value
    }

    override suspend fun getPromotion(id: Long): Promotion? {
        return promotionsFlow.value.find { it.id == id }
    }

    override suspend fun createPromotion(promotion: Promotion): Promotion {
        delay(400)
        val newPromotion = promotion.copy(id = System.currentTimeMillis(), version = 1L)
        val currentList = promotionsFlow.value.toMutableList()
        currentList.add(newPromotion)
        promotionsFlow.value = currentList
        return newPromotion
    }

    override suspend fun updatePromotion(promotion: Promotion): Promotion {
        delay(400)
        val currentList = promotionsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == promotion.id }
        
        if (index != -1) {
            val existing = currentList[index]
            // Simulando el HTTP 409 Conflict Optimistic Concurrency
            if (existing.version != promotion.version) {
                throw IllegalStateException("HTTP 409 Conflict: El recurso fue modificado por otro usuario.")
            }
            val updated = promotion.copy(version = existing.version + 1)
            currentList[index] = updated
            promotionsFlow.value = currentList
            return updated
        } else {
            throw IllegalArgumentException("Promotion not found")
        }
    }

    override suspend fun archivePromotion(id: Long) {
        val currentList = promotionsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(active = false, version = existing.version + 1)
            promotionsFlow.value = currentList
        }
    }

    override suspend fun quoteCart(cart: List<ConfiguredProduct>): OrderPricingPreview {
        delay(200) // Simulación de llamada de red
        
        val subtotal = cart.fold(java.math.BigDecimal.ZERO) { acc, item -> acc + item.total }

        val rewardItems = mutableListOf<ConfiguredProduct>()
        
        for (item in cart) {
            if (item.name.contains("roll", ignoreCase = true)) {
                val reward = ConfiguredProduct(
                    menuItemId = item.menuItemId,
                    name = item.name,
                    quantity = item.quantity,
                    baseUnitPrice = java.math.BigDecimal.ZERO,
                    unitTotal = java.math.BigDecimal.ZERO,
                    total = java.math.BigDecimal.ZERO,
                    groups = emptyList()
                )
                rewardItems.add(reward)
            }
        }

        return OrderPricingPreview(
            subtotal = subtotal,
            adjustments = emptyList(),
            rewardItems = rewardItems,
            total = subtotal 
        )
    }
}

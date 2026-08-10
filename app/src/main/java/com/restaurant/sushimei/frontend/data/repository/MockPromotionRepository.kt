package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import java.util.UUID

class MockPromotionRepository : IPromotionRepository {

    private val promotionsFlow = MutableStateFlow<List<Promotion>>(emptyList())

    init {
        // Inicializar con los fixtures requeridos (Lunes $69 y Jueves 2x1)
        val mondayPromotion = Promotion(
            id = "prom-mon-69",
            name = "Lunes de clásicos $69",
            active = true,
            priority = 100,
            schedule = PromotionSchedule(
                daysOfWeek = setOf(1), // Lunes
                allDay = true
            ),
            target = PromotionTarget(
                type = PromotionTargetType.TAG,
                targetId = "ROLL_CLASSIC",
                displayName = "Rollos clásicos"
            ),
            benefit = PromotionBenefit.FixedUnitPrice(java.math.BigDecimal("69.00")),
            version = 1L
        )

        val thursdayPromotion = Promotion(
            id = "prom-thu-2x1",
            name = "Jueves 2x1 clásicos",
            active = true,
            priority = 200,
            schedule = PromotionSchedule(
                daysOfWeek = setOf(4), // Jueves
                allDay = true
            ),
            target = PromotionTarget(
                type = PromotionTargetType.TAG,
                targetId = "ROLL_CLASSIC",
                displayName = "Rollos clásicos"
            ),
            benefit = PromotionBenefit.BuyXPayY(
                buyQuantity = 2,
                payQuantity = 1,
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

    override suspend fun getPromotion(id: String): Promotion? {
        return promotionsFlow.value.find { it.id == id }
    }

    override suspend fun createPromotion(promotion: Promotion): Promotion {
        delay(400)
        val newPromotion = promotion.copy(id = UUID.randomUUID().toString(), version = 1L)
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

    override suspend fun archivePromotion(id: String) {
        val currentList = promotionsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(active = false, version = existing.version + 1)
            promotionsFlow.value = currentList
        }
    }

    /**
     * Fake implementation that returns pre-computed responses for UI development.
     * Does NOT execute real pricing algorithms locally.
     * Instead, it intercepts specific known combinations of Cart items to return hardcoded adjustments.
     */
    override suspend fun quoteCart(cart: List<ConfiguredProduct>): OrderPricingPreview {
        delay(200) // Simulación de llamada de red
        
        // FASE 6A3: REGLA ESTRICTA - NO IMPLEMENTAR ALGORITMOS DE PRECIOS LOCALMENTE
        // Este mock devuelve fixtures precalculados (respuestas falsas transparentes) 
        // para propósitos de UI/Demo. NO hace matemáticas, comprobación de días, ni agrupaciones.

        val subtotal = cart.fold(java.math.BigDecimal.ZERO) { acc, item -> acc + item.total }

        // BOGO Logic para el Jueves (aplicamos dinámicamente si hay rollos para demostrar la API)
        // Detectamos si es un "rollo clásico" por el nombre para el mock.
        val rewardItems = mutableListOf<ConfiguredProduct>()
        
        for (item in cart) {
            if (item.name.contains("roll", ignoreCase = true)) {
                // "For every eligible classic roll explicitly purchased, the promotion generates 
                // one additional promotional unit of the SAME menu item."
                val reward = ConfiguredProduct(
                    menuItemId = item.menuItemId,
                    name = item.name,
                    quantity = item.quantity,
                    baseUnitPrice = java.math.BigDecimal.ZERO,
                    unitTotal = java.math.BigDecimal.ZERO,
                    total = java.math.BigDecimal.ZERO,
                    groups = emptyList() // Toppings on either roll are paid normally, base is free
                )
                rewardItems.add(reward)
            }
        }

        return OrderPricingPreview(
            subtotal = subtotal,
            adjustments = emptyList(), // BOGO no usa un adjustment de descuento total, usa rewardItems
            rewardItems = rewardItems,
            total = subtotal // El total base a pagar no cambia por el reward (el reward es $0)
        )
    }
}

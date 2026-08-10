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
            benefit = PromotionBenefit.FixedUnitPrice(69.0),
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
        
        val subtotal = cart.sumOf { it.total }
        val adjustments = mutableListOf<PricingAdjustment>()

        // Para propósito de demostración en el POS MVP: 
        // Si el carrito tiene 2 rollos "California" (ej. precio catálogo 79), hardcodeamos el Jueves 2x1.
        // Si tiene 1 rollo "California", hardcodeamos el Lunes a $69.
        
        val californiaItems = cart.filter { it.name.contains("California", ignoreCase = true) }
        val totalCaliforniaQty = californiaItems.sumOf { it.quantity }
        
        if (totalCaliforniaQty >= 2) {
            // FIXTURE: Jueves 2x1 (compran 2, pagan 1, se descuentan 79.00 por cada par)
            val pares = totalCaliforniaQty / 2
            val descuento = pares * 79.0 // asumiendo que el California cuesta 79
            adjustments.add(
                PricingAdjustment(
                    label = "Jueves 2x1 clásicos",
                    amount = -descuento,
                    promotionId = "prom-thu-2x1"
                )
            )
        } else if (totalCaliforniaQty == 1) {
            // FIXTURE: Lunes $69 (precio normal 79, se descuentan 10)
            // Asumiendo que el California cuesta 79 en el FakeMenuRepository
            // Solo aplicamos si el precio base era mayor a 69
            val california = californiaItems.first()
            if (california.baseUnitPrice == 79.0) {
                adjustments.add(
                    PricingAdjustment(
                        label = "Lunes de clásicos $69",
                        amount = -10.0,
                        promotionId = "prom-mon-69"
                    )
                )
            }
        }

        val totalAdjustments = adjustments.sumOf { it.amount }
        val finalTotal = subtotal + totalAdjustments

        return OrderPricingPreview(
            subtotal = subtotal,
            adjustments = adjustments,
            total = finalTotal
        )
    }
}

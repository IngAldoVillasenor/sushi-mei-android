package com.restaurant.sushimei.frontend.ui.shared

import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
import java.math.BigDecimal

object CollectionOrchestrator {

    data class ValidatedRequest(
        val method: PaymentMethod,
        val denomination: BigDecimal?
    )

    fun validateCollection(
        orderStatus: String,
        requiresPaymentCollection: Boolean,
        orderTotal: BigDecimal?,
        method: PaymentMethod,
        denomination: BigDecimal?
    ): ValidatedRequest {
        if (orderStatus != "READY" || !requiresPaymentCollection) {
            throw IllegalArgumentException("La orden no está disponible para cobro.")
        }
        var normalizedDenomination = denomination
        if (method == PaymentMethod.CASH) {
            if (normalizedDenomination == null || (orderTotal != null && normalizedDenomination < orderTotal)) {
                throw IllegalArgumentException("Denominación inválida para pago en efectivo.")
            }
        } else {
            normalizedDenomination = null
        }
        return ValidatedRequest(method, normalizedDenomination)
    }

    suspend fun executeReconciliation(
        repo: IOperationalOrderRepository,
        orderId: Long
    ): ReconciliationOutcome {
        val raw = repo.getOperationalActiveOrders()
        val stillPresent = raw.any { it.id == orderId && it.status == "READY" && it.requiresPaymentCollection }
        if (stillPresent) {
            return ReconciliationOutcome.Retryable("Fallo de red. La orden no se cobró. Inténtalo de nuevo.")
        } else {
            val detail = repo.getOperationalOrderDetail(orderId)
            if (detail.status == "COMPLETED" &&
                !detail.requiresPaymentCollection &&
                detail.paymentMethod != null &&
                detail.paymentCollectedAt != null &&
                detail.paymentCollectedByUserId != null
            ) {
                return ReconciliationOutcome.Success
            } else {
                return ReconciliationOutcome.Uncertain("Fallo de red. El estado del cobro es incierto. Refresca las órdenes antes de intentar de nuevo.")
            }
        }
    }

    sealed interface ReconciliationOutcome {
        object Success : ReconciliationOutcome
        data class Retryable(val message: String) : ReconciliationOutcome
        data class Uncertain(val message: String) : ReconciliationOutcome
    }

    fun mapCollectionApiError(e: ApiException): String {
        return when (e.code) {
            "ORDER_PAYMENT_COLLECTION_REQUIRED" -> "Se requiere el cobro de la orden."
            "ORDER_PAYMENT_COLLECTION_NOT_SUPPORTED" -> "Esta orden no soporta cobro local."
            "ORDER_INVALID_PAYMENT_COLLECTION_REQUEST" -> "Datos de cobro inválidos."
            "ORDER_PAYMENT_COLLECTION_BUSINESS_DAY_NOT_OPEN" -> "No hay un día operativo abierto para cobrar."
            "ORDER_INVALID_TRANSITION" -> "La orden no se encuentra en estado válido para cobro."
            "ORDER_NOT_FOUND" -> "La orden ya no existe."
            else -> "Error al cobrar: ${e.message ?: "Error de servidor"}"
        } + e.referenceSuffix()
    }
}

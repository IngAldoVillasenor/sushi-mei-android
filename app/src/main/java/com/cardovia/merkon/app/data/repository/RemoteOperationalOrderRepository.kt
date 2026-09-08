package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.api.MerkonApi
import com.cardovia.merkon.app.data.model.OperationalOrderDetailDto
import com.cardovia.merkon.app.data.model.VoidOrderRequest
import com.cardovia.merkon.app.data.model.VoidOrderResponse

class RemoteOperationalOrderRepository(private val api: MerkonApi) : IOperationalOrderRepository {
    override suspend fun getOperationalActiveOrders(): List<com.cardovia.merkon.app.data.model.OperationalOrderSummaryDto> {
        val response = api.getOperationalActiveOrders()
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getOperationalAnalytics(from: String, to: String): com.cardovia.merkon.app.data.model.HistoricalAnalyticsResponse {
        val response = api.getOperationalAnalytics(from, to)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty body")
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getHistoricalOrders(
        from: String?,
        to: String?,
        source: String?,
        status: String?,
        page: Int?,
        size: Int?
    ): com.cardovia.merkon.app.data.model.HistoricalOrdersPageDto {
        val response = api.getHistoricalOrders(from, to, source, status, page, size)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty body")
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getOperationalOrderDetail(orderId: Long): OperationalOrderDetailDto {
        val response = api.getOperationalOrderDetail(orderId)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty body")
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun voidOrder(
        orderId: Long,
        reason: String
    ): VoidOrderResponse {
        val response = api.voidOrder(orderId, VoidOrderRequest(reason = reason))
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty body")
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun collectPayment(
        orderId: Long,
        paymentMethod: com.cardovia.merkon.app.data.model.PaymentMethod,
        cashDenomination: java.math.BigDecimal?
    ): com.cardovia.merkon.app.data.model.OrderPaymentCollectionResponse {
        val request = com.cardovia.merkon.app.data.model.OrderPaymentCollectionRequest(
            paymentMethod = paymentMethod,
            cashDenomination = cashDenomination
        )
        val response = api.collectPayment(orderId, request)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        }

        if (response.code() in listOf(502, 503, 504)) {
            throw java.io.IOException("Gateway Error: ${response.code()}")
        }

        val errorBodyString = try { response.errorBody()?.string() } catch (e: Exception) { null }
        if (!errorBodyString.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(errorBodyString)
                val code = json.optString("code", "UNKNOWN_ERROR")
                val message = json.optString("message", "Error al cobrar orden")
                throw com.cardovia.merkon.app.data.api.ApiException(code, message)
            } catch (e: Exception) {
                if (e is com.cardovia.merkon.app.data.api.ApiException) throw e
            }
        }
        throw com.cardovia.merkon.app.data.api.ApiException("HTTP_ERROR", "Error: HTTP ${response.code()}")
    }
}

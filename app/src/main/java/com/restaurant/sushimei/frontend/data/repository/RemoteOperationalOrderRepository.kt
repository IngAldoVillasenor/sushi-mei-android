package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.VoidOrderRequest
import com.restaurant.sushimei.frontend.data.model.VoidOrderResponse

class RemoteOperationalOrderRepository(private val api: SushiMeiApi) : IOperationalOrderRepository {
    override suspend fun getOperationalActiveOrders(): List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto> {
        val response = api.getOperationalActiveOrders()
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getOperationalAnalytics(from: String, to: String): com.restaurant.sushimei.frontend.data.model.HistoricalAnalyticsResponse {
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
    ): com.restaurant.sushimei.frontend.data.model.HistoricalOrdersPageDto {
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
        paymentMethod: com.restaurant.sushimei.frontend.data.model.PaymentMethod,
        cashDenomination: java.math.BigDecimal?
    ): com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse {
        val request = com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionRequest(
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
                throw com.restaurant.sushimei.frontend.data.api.ApiException(code, message)
            } catch (e: Exception) {
                if (e is com.restaurant.sushimei.frontend.data.api.ApiException) throw e
            }
        }
        throw com.restaurant.sushimei.frontend.data.api.ApiException("HTTP_ERROR", "Error: HTTP ${response.code()}")
    }
}

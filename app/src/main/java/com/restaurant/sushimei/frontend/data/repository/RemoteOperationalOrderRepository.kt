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
}

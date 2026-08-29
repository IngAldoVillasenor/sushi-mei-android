package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto

interface IOperationalOrderRepository {
    suspend fun getOperationalOrderDetail(orderId: Long): OperationalOrderDetailDto

    suspend fun getOperationalActiveOrders(): List<com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto>
    suspend fun getOperationalAnalytics(from: String, to: String): com.restaurant.sushimei.frontend.data.model.HistoricalAnalyticsResponse

    suspend fun getHistoricalOrders(
        from: String? = null,
        to: String? = null,
        source: String? = null,
        status: String? = null,
        page: Int? = null,
        size: Int? = null
    ): com.restaurant.sushimei.frontend.data.model.HistoricalOrdersPageDto

    suspend fun voidOrder(
        orderId: Long,
        reason: String
    ): com.restaurant.sushimei.frontend.data.model.VoidOrderResponse
}

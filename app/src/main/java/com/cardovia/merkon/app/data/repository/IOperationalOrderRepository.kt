package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.model.OperationalOrderDetailDto

interface IOperationalOrderRepository {
    suspend fun getOperationalOrderDetail(orderId: Long): OperationalOrderDetailDto

    suspend fun getOperationalActiveOrders(): List<com.cardovia.merkon.app.data.model.OperationalOrderSummaryDto>
    suspend fun getOperationalAnalytics(from: String, to: String): com.cardovia.merkon.app.data.model.HistoricalAnalyticsResponse

    suspend fun getHistoricalOrders(
        from: String? = null,
        to: String? = null,
        source: String? = null,
        status: String? = null,
        page: Int? = null,
        size: Int? = null
    ): com.cardovia.merkon.app.data.model.HistoricalOrdersPageDto

    suspend fun voidOrder(
        orderId: Long,
        reason: String
    ): com.cardovia.merkon.app.data.model.VoidOrderResponse

    suspend fun collectPayment(
        orderId: Long,
        paymentMethod: com.cardovia.merkon.app.data.model.PaymentMethod,
        cashDenomination: java.math.BigDecimal?
    ): com.cardovia.merkon.app.data.model.OrderPaymentCollectionResponse
}

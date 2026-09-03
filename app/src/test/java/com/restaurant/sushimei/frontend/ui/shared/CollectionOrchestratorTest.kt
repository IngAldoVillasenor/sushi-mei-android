package com.restaurant.sushimei.frontend.ui.shared

import com.restaurant.sushimei.frontend.data.model.FulfillmentType
import com.restaurant.sushimei.frontend.data.model.HistoricalAnalyticsResponse
import com.restaurant.sushimei.frontend.data.model.HistoricalOrdersPageDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto
import com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse
import com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import com.restaurant.sushimei.frontend.data.model.VoidOrderResponse
import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CollectionOrchestratorTest {

    class FakeRepo(
        var activeOrders: List<OperationalOrderSummaryDto> = emptyList(),
        var orderDetail: OperationalOrderDetailDto? = null
    ) : IOperationalOrderRepository {
        override suspend fun getOperationalActiveOrders(): List<OperationalOrderSummaryDto> = activeOrders
        override suspend fun getOperationalOrderDetail(orderId: Long): OperationalOrderDetailDto =
            orderDetail ?: throw Exception("Not found")

        override suspend fun getOperationalAnalytics(from: String, to: String): HistoricalAnalyticsResponse {
            throw UnsupportedOperationException()
        }
        override suspend fun getHistoricalOrders(
            from: String?, to: String?, source: String?, status: String?, page: Int?, size: Int?
        ): HistoricalOrdersPageDto { throw UnsupportedOperationException() }

        override suspend fun voidOrder(orderId: Long, reason: String): VoidOrderResponse {
            throw UnsupportedOperationException()
        }
        override suspend fun collectPayment(
            orderId: Long,
            paymentMethod: PaymentMethod,
            cashDenomination: BigDecimal?
        ): OrderPaymentCollectionResponse { throw UnsupportedOperationException() }
    }

    private fun makeSummary(id: Long, status: String, requiresPaymentCollection: Boolean = true) =
        OperationalOrderSummaryDto(
            id = id,
            status = status,
            orderSource = "POS",
            fulfillmentType = FulfillmentType.PICKUP,
            paymentMethod = null,
            paymentTiming = OrderPaymentTiming.ON_DELIVERY,
            requiresPaymentCollection = requiresPaymentCollection,
            deliveryAddress = null,
            pickupName = "TestUser",
            cashDenomination = null,
            phoneNumber = null,
            total = BigDecimal("100.00"),
            createdAt = Instant.now(),
            requiresPaymentValidation = false,
            structuredLinesAvailable = false
        )

    private fun makeDetail(id: Long, status: String, requiresPaymentCollection: Boolean = true) =
        OperationalOrderDetailDto(
            id = id,
            status = status,
            requestId = null,
            orderSource = "POS",
            createdByUserId = null,
            fulfillmentType = FulfillmentType.PICKUP,
            paymentMethod = null,
            requiresPaymentCollection = requiresPaymentCollection,
            paymentCollectedAt = null,
            paymentCollectedByUserId = null,
            deliveryAddress = null,
            pickupName = "TestUser",
            cashDenomination = null,
            phoneNumber = null,
            transferReceiptPath = null,
            paymentNotes = null,
            createdAt = Instant.now(),
            total = BigDecimal("100.00"),
            legacyOrderDetails = null,
            lines = emptyList()
        )

    @Test
    fun `reconciliation on order requiring collection but status NOT READY classifies as Uncertain not Retryable`() = runTest {
        // Order is in active-orders with requiresPaymentCollection=true but status PREPARING (not READY)
        val repo = FakeRepo(
            activeOrders = listOf(makeSummary(1L, "PREPARING")),
            orderDetail = makeDetail(1L, "PREPARING")
        )

        val result = CollectionOrchestrator.executeReconciliation(repo, 1L)
        assertTrue(
            "Expected Uncertain for non-READY order requiring collection, but got $result",
            result is CollectionOrchestrator.ReconciliationOutcome.Uncertain
        )
    }

    @Test
    fun `reconciliation on order that is READY and requiresPaymentCollection is Retryable`() = runTest {
        val repo = FakeRepo(
            activeOrders = listOf(makeSummary(2L, "READY"))
        )

        val result = CollectionOrchestrator.executeReconciliation(repo, 2L)
        assertTrue(
            "Expected Retryable for READY order requiring collection, but got $result",
            result is CollectionOrchestrator.ReconciliationOutcome.Retryable
        )
    }
}

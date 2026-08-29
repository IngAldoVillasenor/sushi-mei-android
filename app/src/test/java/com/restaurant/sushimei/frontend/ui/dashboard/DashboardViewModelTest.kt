package com.restaurant.sushimei.frontend.ui.dashboard



import com.restaurant.sushimei.frontend.data.model.HistoricalOrderSummaryDto

import com.restaurant.sushimei.frontend.data.model.HistoricalOrdersPageDto

import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto

import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository

import io.mockk.coEvery

import io.mockk.mockk

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.test.*

import org.junit.After

import org.junit.Assert.*

import org.junit.Before

import org.junit.Test

import java.math.BigDecimal

import java.time.Instant



@OptIn(ExperimentalCoroutinesApi::class)

class DashboardViewModelTest {



    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: IOperationalOrderRepository

    private lateinit var viewModel: DashboardViewModel



    @Before

    fun setUp() {

        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)

        coEvery { repository.getOperationalAnalytics(any(), any()) } returns com.restaurant.sushimei.frontend.data.model.HistoricalAnalyticsResponse(

            from = "2026-08-20T00:00:00Z",

            to = "2026-08-21T00:00:00Z",

            completedRevenue = BigDecimal("5000.00"),

            completedOrderCount = 25,

            averageCompletedTicket = BigDecimal("200.00"),

            voidedOrderCount = 2,

            salesBySource = emptyList()

        )

    }



    @After

    fun tearDown() {

        Dispatchers.resetMain()

    }



    @Test

    fun `date-range calculates correct UTC Instants for America_Mexico_City`() = runTest {

        coEvery { repository.getOperationalActiveOrders() } returns emptyList()

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), any(), any()) } returns HistoricalOrdersPageDto(

            content = emptyList(),

            page = 0,

            size = 100,

            totalElements = 0,

            totalPages = 0

        )

        viewModel = DashboardViewModel(repository)

        advanceUntilIdle()



        val fromSlot = io.mockk.slot<String>()

        val toSlot = io.mockk.slot<String>()



        io.mockk.coVerify {

            repository.getOperationalAnalytics(

                from = capture(fromSlot),

                to = capture(toSlot)

            )

        }



        assertNotNull(fromSlot.captured)

        assertNotNull(toSlot.captured)

        assertTrue(fromSlot.captured.endsWith("Z"))

        assertTrue(toSlot.captured.endsWith("Z"))

    }



    @Test

    fun `loading an additional historical page does NOT change dashboard financial metrics`() = runTest {

        val completedOrderPage0 = HistoricalOrderSummaryDto(

            id = 1,

            externalOrderId = null,

            externalReference = null,

            orderSource = "COUNTER",

            status = "COMPLETED",

            fulfillmentType = null,

            paymentMethod = null,

            pickupName = null,

            total = BigDecimal("150.00"),

            createdAt = Instant.now(),

            structuredLinesAvailable = true

        )



        val completedOrderPage1 = HistoricalOrderSummaryDto(

            id = 2,

            externalOrderId = null,

            externalReference = null,

            orderSource = "COUNTER",

            status = "COMPLETED",

            fulfillmentType = null,

            paymentMethod = null,

            pickupName = null,

            total = BigDecimal("300.00"),

            createdAt = Instant.now(),

            structuredLinesAvailable = true

        )



        coEvery { repository.getOperationalActiveOrders() } returns listOf(

            mockk(relaxed = true), mockk(relaxed = true) // 2 active orders

        )



        // First page

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), 0, any()) } returns HistoricalOrdersPageDto(

            content = listOf(completedOrderPage0),

            page = 0,

            size = 100,

            totalElements = 2,

            totalPages = 2

        )



        // Second page

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), 1, any()) } returns HistoricalOrdersPageDto(

            content = listOf(completedOrderPage1),

            page = 1,

            size = 100,

            totalElements = 2,

            totalPages = 2

        )



        viewModel = DashboardViewModel(repository)

        advanceUntilIdle()



        val stateAfterInit = viewModel.uiState.value as DashboardUiState.Content

        // Active orders should be correct

        assertEquals(2, stateAfterInit.metrics.activeOrderCount)

        // Financial metrics must reflect the mock analytics

        assertEquals(BigDecimal("5000.00"), stateAfterInit.metrics.completedSalesTotal)

        assertEquals(25, stateAfterInit.metrics.completedOrderCount)

        assertEquals(1, stateAfterInit.orders.size) // 1 order loaded in list



        // Load next page

        viewModel.loadMore()

        advanceUntilIdle()



        val stateAfterLoadMore = viewModel.uiState.value as DashboardUiState.Content

        // Active orders still correct

        assertEquals(2, stateAfterLoadMore.metrics.activeOrderCount)

        // Financial metrics MUST NOT HAVE CHANGED

        assertEquals(BigDecimal("5000.00"), stateAfterLoadMore.metrics.completedSalesTotal)

        assertEquals(25, stateAfterLoadMore.metrics.completedOrderCount)

        assertEquals(2, stateAfterLoadMore.orders.size) // 2 orders loaded in list

    }



    @Test

    fun `network error state`() = runTest {

        coEvery { repository.getOperationalActiveOrders() } throws RuntimeException("Network Error")



        viewModel = DashboardViewModel(repository)

        advanceUntilIdle()



        val state = viewModel.uiState.value as DashboardUiState.Error

        assertEquals("Network Error", state.message)

    }



    @Test

    fun `refresh recovers from error`() = runTest {

        var callCount = 0

        coEvery { repository.getOperationalActiveOrders() } answers {

            if (callCount++ == 0) throw RuntimeException("Error 1")

            emptyList()

        }

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), any(), any()) } returns HistoricalOrdersPageDto(

            content = emptyList(),

            page = 0,

            size = 100,

            totalElements = 0,

            totalPages = 0

        )



        viewModel = DashboardViewModel(repository)

        advanceUntilIdle()



        assertTrue(viewModel.uiState.value is DashboardUiState.Error)



        viewModel.refresh()

        advanceUntilIdle()



        assertTrue(viewModel.uiState.value is DashboardUiState.Content)

    }



    @Test

    fun `pagination error is recoverable and does not destroy dashboard`() = runTest {

        coEvery { repository.getOperationalActiveOrders() } returns emptyList()

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), 0, any()) } returns HistoricalOrdersPageDto(

            content = emptyList(),

            page = 0,

            size = 100,

            totalElements = 200,

            totalPages = 2

        )

        // Fails on page 1

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), 1, any()) } throws RuntimeException("Pagination failed")



        viewModel = DashboardViewModel(repository)

        advanceUntilIdle()



        val stateAfterInit = viewModel.uiState.value as DashboardUiState.Content

        assertTrue(stateAfterInit.hasMore)

        assertNull(stateAfterInit.paginationError)



        viewModel.loadMore()

        advanceUntilIdle()



        val stateAfterError = viewModel.uiState.value as DashboardUiState.Content

        assertEquals("Pagination failed", stateAfterError.paginationError)

        assertFalse(stateAfterError.isPaginating)

        assertTrue(stateAfterError.hasMore) // Still has more, can retry



        // Next attempt succeeds

        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), 1, any()) } returns HistoricalOrdersPageDto(

            content = listOf(mockk(relaxed = true)),

            page = 1,

            size = 100,

            totalElements = 200,

            totalPages = 2

        )



        viewModel.loadMore()

        advanceUntilIdle()



        val stateAfterRetry = viewModel.uiState.value as DashboardUiState.Content

        assertNull(stateAfterRetry.paginationError)

        assertEquals(1, stateAfterRetry.orders.size)

        assertFalse(stateAfterRetry.hasMore)

    }


    @Test
    fun `TEST 1 - Calling the new detail-load action for an order invokes repository and exposes Loaded state`() = runTest {
        val detail = mockk<com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>(relaxed = true)
        coEvery { repository.getOperationalOrderDetail(42L) } returns detail

        viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.getOperationalOrderDetail(42L) }
        val detailState = viewModel.detailState.value
        assertTrue(detailState is OrderDetailState.Loaded)
        assertEquals(42L, (detailState as OrderDetailState.Loaded).orderId)
    }

    @Test
    fun `TEST 2 - Opening an already successfully loaded order again does NOT trigger another repository request`() = runTest {
        val detail = mockk<com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>(relaxed = true)
        coEvery { repository.getOperationalOrderDetail(42L) } returns detail

        viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()

        viewModel.closeOrderDetail()
        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()

        // Should only be called once because it is cached
        io.mockk.coVerify(exactly = 1) { repository.getOperationalOrderDetail(42L) }
        val detailState = viewModel.detailState.value
        assertTrue(detailState is OrderDetailState.Loaded)
    }

    @Test
    fun `TEST 3 - A repository exception produces a recoverable detail Error state without changing the main Dashboard Content state`() = runTest {
        coEvery { repository.getOperationalActiveOrders() } returns emptyList()
        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), any(), any()) } returns HistoricalOrdersPageDto(
            content = emptyList(), page = 0, size = 100, totalElements = 0, totalPages = 0
        )

        viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        coEvery { repository.getOperationalOrderDetail(42L) } throws RuntimeException("Detail load failed")

        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()

        val detailState = viewModel.detailState.value
        assertTrue(detailState is OrderDetailState.Error)
        assertEquals("Detail load failed", (detailState as OrderDetailState.Error).message)

        // Main content state remains unaffected
        assertTrue(viewModel.uiState.value is DashboardUiState.Content)
    }

    @Test
    fun `TEST 4 - Retrying after an Error calls the repository again and can transition to Loaded`() = runTest {
        var callCount = 0
        val detail = mockk<com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>(relaxed = true)
        coEvery { repository.getOperationalOrderDetail(42L) } answers {
            if (callCount++ == 0) throw RuntimeException("Temporary Error")
            detail
        }

        viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        // First try -> Error
        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()
        assertTrue(viewModel.detailState.value is OrderDetailState.Error)

        // Retry -> Loaded
        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()

        assertTrue(viewModel.detailState.value is OrderDetailState.Loaded)
        io.mockk.coVerify(exactly = 2) { repository.getOperationalOrderDetail(42L) }
    }

    @Test
    fun `TEST 5 - Loading order detail does not discard current historical orders, existing financial metrics, or pagination state`() = runTest {
        coEvery { repository.getOperationalActiveOrders() } returns emptyList()
        val pageDto = HistoricalOrdersPageDto(
            content = listOf(mockk(relaxed = true)), page = 0, size = 100, totalElements = 200, totalPages = 2
        )
        coEvery { repository.getHistoricalOrders(any(), any(), any(), any(), 0, any()) } returns pageDto

        viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value as DashboardUiState.Content
        val initialMetrics = initialState.metrics
        val initialOrders = initialState.orders
        val initialHasMore = initialState.hasMore

        // Load order detail
        coEvery { repository.getOperationalOrderDetail(42L) } returns mockk(relaxed = true)
        viewModel.loadOrderDetail(42L)
        advanceUntilIdle()

        val postState = viewModel.uiState.value as DashboardUiState.Content
        assertEquals(initialMetrics, postState.metrics)
        assertEquals(initialOrders, postState.orders)
        assertEquals(initialHasMore, postState.hasMore)
    }

}

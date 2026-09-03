package com.restaurant.sushimei.frontend

import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockApi: SushiMeiApi
    private lateinit var mockRepository: IOrderRepository
    private lateinit var mockOperationalRepo: com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
    private lateinit var viewModel: KitchenViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApi = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockOperationalRepo = mockk(relaxed = true)
        viewModel = KitchenViewModel(mockRepository, mockApi, autoStartPolling = false, operationalOrderRepository = mockOperationalRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `PREPARING action invokes markOrderReady and refreshes data`() = runTest {
        coEvery { mockApi.markOrderReady(123L) } returns Response.success(Unit)
        coEvery { mockApi.getOperationalOrderDetail(123L) } returns Response.success(null)
        coEvery { mockApi.getOperationalActiveOrders() } returns Response.success(emptyList())

        viewModel.markOperationalOrderReady(123L)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockApi.markOrderReady(123L) }
        coVerify(exactly = 1) { mockApi.getOperationalOrderDetail(123L) }
        coVerify(exactly = 1) { mockApi.getOperationalActiveOrders() }
        coVerify(exactly = 0) { mockOperationalRepo.collectPayment(any(), any(), any()) }
    }

    @Test
    fun `READY action invokes completeOrder and refreshes data`() = runTest {
        coEvery { mockApi.completeOrder(456L) } returns Response.success(Unit)
        coEvery { mockApi.getOperationalOrderDetail(456L) } returns Response.success(null)
        coEvery { mockApi.getOperationalActiveOrders() } returns Response.success(emptyList())

        viewModel.completeOperationalOrder(456L)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockApi.completeOrder(456L) }
        coVerify(exactly = 1) { mockApi.getOperationalOrderDetail(456L) }
        coVerify(exactly = 1) { mockApi.getOperationalActiveOrders() }
        coVerify(exactly = 0) { mockOperationalRepo.collectPayment(any(), any(), any()) }
    }

    @Test
    fun `ApiException produces an API or action error message`() = runTest {
        coEvery { mockApi.markOrderReady(123L) } throws ApiException("400", "State transition invalid")

        viewModel.markOperationalOrderReady(123L)
        advanceUntilIdle()

        assertEquals("Rechazo del servidor: State transition invalid", viewModel.kitchenError.value)
    }

    @Test
    fun `IOException produces a network error message`() = runTest {
        coEvery { mockApi.markOrderReady(123L) } throws IOException("No internet")

        viewModel.markOperationalOrderReady(123L)
        advanceUntilIdle()

        assertEquals("Error de red al marcar como listo.", viewModel.kitchenError.value)
    }

    @Test
    fun `acceptOperationalOrder prepares before fetching detail for printing`() = runTest {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val dummyUsbManager = mockk<android.hardware.usb.UsbManager>(relaxed = true)
        val dummyBluetoothManager = mockk<android.bluetooth.BluetoothManager>(relaxed = true)
        io.mockk.every { mockContext.getSystemService(android.content.Context.USB_SERVICE) } returns dummyUsbManager
        io.mockk.every { mockContext.getSystemService(android.content.Context.BLUETOOTH_SERVICE) } returns dummyBluetoothManager
        val dummyDetail = mockk<com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>(relaxed = true)

        coEvery { mockApi.getOperationalOrderDetail(777L) } returns Response.success(dummyDetail)
        coEvery { mockApi.acceptAndPrepareOrder(777L) } returns Response.success(Unit)

        viewModel.acceptOperationalOrder(777L, mockContext)
        advanceUntilIdle()

        coVerifyOrder {
            mockApi.acceptAndPrepareOrder(777L)
            mockApi.getOperationalOrderDetail(777L)
        }
    }

    @Test
    fun `acceptOperationalOrder keeps preparing transition when ticket detail fetch fails`() = runTest {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val dummyUsbManager = mockk<android.hardware.usb.UsbManager>(relaxed = true)
        val dummyBluetoothManager = mockk<android.bluetooth.BluetoothManager>(relaxed = true)
        io.mockk.every { mockContext.getSystemService(android.content.Context.USB_SERVICE) } returns dummyUsbManager
        io.mockk.every { mockContext.getSystemService(android.content.Context.BLUETOOTH_SERVICE) } returns dummyBluetoothManager

        coEvery { mockApi.getOperationalOrderDetail(888L) } returns Response.error(404, okhttp3.ResponseBody.create(null, ""))
        coEvery { mockApi.acceptAndPrepareOrder(888L) } returns Response.success(Unit)
        coEvery { mockApi.getOperationalActiveOrders() } returns Response.success(emptyList())

        viewModel.acceptOperationalOrder(888L, mockContext)
        advanceUntilIdle()

        coVerifyOrder {
            mockApi.acceptAndPrepareOrder(888L)
            mockApi.getOperationalOrderDetail(888L)
        }
        assertEquals(
            "La orden se aceptó, pero no se pudo cargar el ticket para imprimir.",
            viewModel.kitchenError.value
        )
    }

    @Test
    fun `acceptOperationalOrder does not fetch or print a ticket when prepare is rejected`() = runTest {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        coEvery { mockApi.acceptAndPrepareOrder(999L) } returns Response.error(
            409,
            okhttp3.ResponseBody.create(null, "")
        )

        viewModel.acceptOperationalOrder(999L, mockContext)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockApi.acceptAndPrepareOrder(999L) }
        coVerify(exactly = 0) { mockApi.getOperationalOrderDetail(999L) }
        assertEquals(
            "Error del servidor: Rechazo en operación (HTTP 409)",
            viewModel.kitchenError.value
        )
    }

    // --- Pay on Delivery V2 Collection Tests ---

    private fun makeOperationalOrderSummary(
        id: Long = 1L,
        status: String = "READY",
        requiresPaymentCollection: Boolean = false,
        total: java.math.BigDecimal? = null,
        paymentMethod: com.restaurant.sushimei.frontend.data.model.PaymentMethod? = com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH,
        cashDenomination: java.math.BigDecimal? = null
    ) = com.restaurant.sushimei.frontend.data.model.OperationalOrderSummaryDto(
        id = id,
        createdAt = java.time.Instant.parse("2023-10-01T12:00:00Z"),
        status = status,
        fulfillmentType = com.restaurant.sushimei.frontend.data.model.FulfillmentType.DELIVERY,
        paymentMethod = paymentMethod,
        paymentTiming = com.restaurant.sushimei.frontend.data.model.OrderPaymentTiming.ON_DELIVERY,
        pickupName = null,
        deliveryAddress = "Test",
        total = total,
        cashDenomination = cashDenomination,
        requiresPaymentCollection = requiresPaymentCollection,
        orderSource = "MANUAL",
        phoneNumber = null,
        requiresPaymentValidation = false,
        structuredLinesAvailable = true
    )

    @Test
    fun `test openCollectionConfirmation populates state and clears errors`() = runTest(testDispatcher) {
        viewModel.openCollectionConfirmation(1L)
        assertEquals(1L, viewModel.collectionConfirmationOrderId.value)
        assertNull(viewModel.collectionError.value)
        assertNull(viewModel.collectionSuccessMessage.value)
    }

    @Test
    fun `test closeCollectionConfirmation clears state if not in flight`() = runTest(testDispatcher) {
        viewModel.openCollectionConfirmation(1L)
        viewModel.closeCollectionConfirmation()
        assertNull(viewModel.collectionConfirmationOrderId.value)
    }

    @Test
    fun `test closeCollectionConfirmation ignores if in flight`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))

        val deferred = kotlinx.coroutines.CompletableDeferred<com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse>()
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } coAnswers { deferred.await() }

        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        viewModel.openCollectionConfirmation(1L)
        viewModel.closeCollectionConfirmation()
        assertEquals(1L, viewModel.collectionConfirmationOrderId.value)

        deferred.complete(mockk())
    }

    @Test
    fun `test submitCollection ignores if already in flight`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))

        val deferred = kotlinx.coroutines.CompletableDeferred<com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse>()
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } coAnswers { deferred.await() }

        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockOperationalRepo.collectPayment(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null) }
        coVerify(exactly = 0) { mockApi.completeOrder(any()) }
        deferred.complete(mockk())
    }

    @Test
    fun `test submitCollection sets error if order missing`() = runTest(testDispatcher) {
        viewModel.submitCollection(999L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        assertEquals("La orden no está disponible para cobro.", viewModel.collectionError.value)
    }

    @Test
    fun `test submitCollection validates CASH amount correctly (valid)`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } returns mockk()
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH, java.math.BigDecimal("150.00"))
        advanceUntilIdle()

        coVerify { mockOperationalRepo.collectPayment(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH, java.math.BigDecimal("150.00")) }
    }

    @Test
    fun `test submitCollection validates CASH amount correctly (invalid)`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CASH, java.math.BigDecimal("50.00"))
        advanceUntilIdle()

        assertNotNull(viewModel.collectionError.value)
        coVerify(exactly = 0) { mockOperationalRepo.collectPayment(any(), any(), any()) }
    }

    @Test
    fun `test submitCollection validates requiresPaymentCollection == true`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = false, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertNotNull(viewModel.collectionError.value)
        coVerify(exactly = 0) { mockOperationalRepo.collectPayment(any(), any(), any()) }
    }

    @Test
    fun `test submitCollection validates status == READY`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "PREPARING", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertNotNull(viewModel.collectionError.value)
        coVerify(exactly = 0) { mockOperationalRepo.collectPayment(any(), any(), any()) }
    }

    @Test
    fun `test submitCollection invokes collectPayment exactly once on success`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } returns mockk()
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockOperationalRepo.collectPayment(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null) }
        coVerify(exactly = 0) { mockApi.completeOrder(any()) }
        assertEquals("Cobro registrado correctamente.", viewModel.collectionSuccessMessage.value)
        assertNull(viewModel.collectionInFlightOrderId.value)
    }

    @Test
    fun `test submitCollection maps ApiException on validation failure`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws com.restaurant.sushimei.frontend.data.api.ApiException("ORDER_INVALID_TRANSITION", "Invalid transition")
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertTrue(viewModel.collectionError.value!!.contains("estado"))
    }

    @Test
    fun `test submitCollection maps ApiException on business rule failure`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws com.restaurant.sushimei.frontend.data.api.ApiException("ORDER_INVALID_PAYMENT_COLLECTION_REQUEST", "Datos insuficientes")
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertTrue(viewModel.collectionError.value!!.contains("Datos de cobro"))
    }

    @Test
    fun `test submitCollection mapping handles unexpected ApiException code`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws com.restaurant.sushimei.frontend.data.api.ApiException("UNKNOWN_ERROR", "Something broke")
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertTrue(viewModel.collectionError.value!!.contains("Something broke"))
    }

    @Test
    fun `test submitCollection on IOException triggers reconciliation exactly once`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.getOperationalActiveOrders() } returns listOf(order)
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws java.io.IOException("Network error")
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockOperationalRepo.getOperationalActiveOrders() }
        coVerify(exactly = 1) { mockOperationalRepo.collectPayment(any(), any(), any()) }
    }

    @Test
    fun `test submitCollection on IOException with Success outcome clears state`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.getOperationalActiveOrders() } returns listOf() // Gone in reconciliation
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws java.io.IOException("Network error")
        val detail = mockk<com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>(relaxed = true) {
            coEvery { status } returns "COMPLETED"
            coEvery { requiresPaymentCollection } returns false
            coEvery { paymentMethod } returns com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD
            coEvery { paymentCollectedAt } returns java.time.Instant.now()
            coEvery { paymentCollectedByUserId } returns 1L
        }
        coEvery { mockOperationalRepo.getOperationalOrderDetail(any()) } returns detail

        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertEquals("Cobro registrado correctamente.", viewModel.collectionSuccessMessage.value)
    }

    @Test
    fun `test submitCollection on IOException with Retryable outcome surfaces error`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.getOperationalActiveOrders() } returns listOf(order) // Still present
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws java.io.IOException("Network error")

        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertTrue(viewModel.collectionError.value!!.contains("Fallo de red. La orden no se cobró."))
    }

    @Test
    fun `test submitCollection on IOException with Uncertain outcome surfaces warning`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.getOperationalActiveOrders() } returns listOf() // Gone in reconciliation
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws java.io.IOException("Network error")
        val detail = mockk<com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto>(relaxed = true) {
            coEvery { status } returns "READY" // Not completed
        }
        coEvery { mockOperationalRepo.getOperationalOrderDetail(any()) } returns detail

        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertTrue(viewModel.collectionError.value!!.contains("El estado del cobro es incierto."))
    }

    @Test
    fun `test submitCollection fallback catch block surfaces unexpected exception`() = runTest(testDispatcher) {
        val order = makeOperationalOrderSummary(id = 1L, status = "READY", requiresPaymentCollection = true, total = java.math.BigDecimal("100.00"))
        coEvery { mockApi.getOperationalActiveOrders() } returns retrofit2.Response.success(listOf(order))
        coEvery { mockOperationalRepo.collectPayment(any(), any(), any()) } throws IllegalStateException("WTF")
        viewModel.fetchBackendOrders()
        advanceUntilIdle()

        viewModel.submitCollection(1L, com.restaurant.sushimei.frontend.data.model.PaymentMethod.CARD, null)
        advanceUntilIdle()

        assertTrue(viewModel.collectionError.value!!.contains("Error inesperado: WTF"))
    }

}

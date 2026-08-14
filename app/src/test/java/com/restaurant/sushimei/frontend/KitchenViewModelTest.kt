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
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockApi: SushiMeiApi
    private lateinit var mockRepository: IOrderRepository
    private lateinit var viewModel: KitchenViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApi = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        viewModel = KitchenViewModel(mockRepository, mockApi, autoStartPolling = false)
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
}

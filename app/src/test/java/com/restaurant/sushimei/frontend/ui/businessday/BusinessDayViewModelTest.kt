package com.restaurant.sushimei.frontend.ui.businessday

import com.restaurant.sushimei.frontend.PrintManager
import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.BusinessDayStatus
import com.restaurant.sushimei.frontend.data.repository.IBusinessDayRepository
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessDayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: IBusinessDayRepository
    private lateinit var printManager: PrintManager
    private lateinit var printJobRepository: com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
    private lateinit var viewModel: BusinessDayViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        printManager = mockk()
        printJobRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDay(status: BusinessDayStatus, closureId: Long? = 123L, closureNumber: Int? = 1): BusinessDayResponse = BusinessDayResponse(
        businessDayId = 42L,
        businessDate = "2023-01-01",
        status = status,
        openingCashAmount = BigDecimal.ZERO,
        openedAt = Instant.now(),
        openedByUserId = 1L,
        closedAt = Instant.now(),
        closedByUserId = 1L,
        completedSalesAmount = BigDecimal.ZERO,
        cashSalesAmount = BigDecimal.ZERO,
        transferSalesAmount = BigDecimal.ZERO,
        cardSalesAmount = BigDecimal.ZERO,
        unclassifiedSalesAmount = BigDecimal.ZERO,
        completedOrderCount = 0L,
        voidedOrderCount = 0L,
        expectedClosingCashAmount = BigDecimal.ZERO,
        actualClosingCashAmount = BigDecimal.ZERO,
        cashDifferenceAmount = BigDecimal.ZERO,
        closureId = if (status == BusinessDayStatus.CLOSED) closureId else null,
        closureNumber = if (status == BusinessDayStatus.CLOSED) closureNumber else null
    )

    private fun createJob(status: PrintJobStatus, error: String? = null): PrintJobEntity = PrintJobEntity(
        id = "job-42",
        requestId = "close-123",
        documentType = com.restaurant.sushimei.frontend.data.model.PrintDocumentType.BUSINESS_DAY_CLOSE,
        documentId = 123L,
        snapshotPayload = "{}",
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
        printedAt = null,
        lastError = error,
        activeAttemptId = null
    )

    @Test
    fun `closing print enqueues correct documentType and documentId`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val jobFlow = MutableSharedFlow<PrintJobEntity>()
        every { printJobRepository.observeJobById("job-42") } returns jobFlow
        val mockedJob = createJob(PrintJobStatus.PENDING)
        coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns mockedJob

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.printClosingTicket(day)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            printManager.enqueuePrintJob(
                documentType = com.restaurant.sushimei.frontend.data.model.PrintDocumentType.BUSINESS_DAY_CLOSE,
                documentId = 123L,
                requestId = "close-123",
                snapshotPayload = any()
            )
        }
        assertEquals("Cierre agregado a la cola de impresión", viewModel.printMessage.value)
    }

    @Test
    fun `print error reaches UI state`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        val jobFlow = MutableSharedFlow<PrintJobEntity>()
        every { printJobRepository.observeJobById("job-42") } returns jobFlow

        val mockedJob = createJob(PrintJobStatus.PENDING)
        coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns mockedJob

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.printClosingTicket(day)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cierre agregado a la cola de impresión", viewModel.printMessage.value)

        // Now simulate failed state
        jobFlow.emit(createJob(PrintJobStatus.FAILED, "Printer disconnected"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("No se pudo imprimir el cierre: Printer disconnected", viewModel.printMessage.value)
        assertEquals(false, viewModel.isPrinting.value)
    }

    @Test
    fun `print success reaches UI state`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        val jobFlow = MutableSharedFlow<PrintJobEntity>()
        every { printJobRepository.observeJobById("job-42") } returns jobFlow

        val mockedJob = createJob(PrintJobStatus.PENDING)
        coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns mockedJob

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.printClosingTicket(day)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cierre agregado a la cola de impresión", viewModel.printMessage.value)

        // Now simulate printed state
        jobFlow.emit(createJob(PrintJobStatus.PRINTED))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cierre impreso correctamente", viewModel.printMessage.value)
        assertEquals(false, viewModel.isPrinting.value)
    }

    @Test
    fun `BUSINESS_DAY_HAS_ACTIVE_ORDERS produces useful UI message`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        val exceptionMessage = "No se puede cerrar la caja mientras existan órdenes activas."
        coEvery { repository.closeBusinessDay(any()) } returns Result.failure(
            com.restaurant.sushimei.frontend.data.api.ApiException("BUSINESS_DAY_HAS_ACTIVE_ORDERS", exceptionMessage, 400, exceptionMessage)
        )

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.closeBusinessDay(BigDecimal.ZERO)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as BusinessDayState.Error
        assertEquals(exceptionMessage, state.message)
    }

    @Test
    fun `reopenBusinessDay successful transitions to Open`() = runTest(testDispatcher) {
        val closedDay = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(closedDay)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val reopenedDay = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.success(reopenedDay)

        viewModel.reopenBusinessDay()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as BusinessDayState.Open
        assertEquals(reopenedDay, state.day)
        assertEquals("Día reabierto correctamente", viewModel.reopenMessage.value)
    }

    @Test
    fun `reopenBusinessDay BUSINESS_DAY_REOPEN_NOT_ALLOWED shows useful message`() = runTest(testDispatcher) {
        val closedDay = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(closedDay)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val exception = com.restaurant.sushimei.frontend.data.api.ApiException("BUSINESS_DAY_REOPEN_NOT_ALLOWED", "Reopen not allowed")
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.failure(exception)

        viewModel.reopenBusinessDay()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as BusinessDayState.Closed
        assertEquals(closedDay, state.day)
        assertEquals("No está permitido reabrir el día.", viewModel.reopenMessage.value)
    }

    @Test
    fun `reopenBusinessDay BUSINESS_DAY_NOT_CLOSED shows useful message`() = runTest(testDispatcher) {
        val closedDay = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(closedDay)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val exception = com.restaurant.sushimei.frontend.data.api.ApiException("BUSINESS_DAY_NOT_CLOSED", "Not closed")
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.failure(exception)

        viewModel.reopenBusinessDay()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value as BusinessDayState.Closed
        assertEquals(closedDay, state.day)
        assertEquals("El día no está cerrado.", viewModel.reopenMessage.value)
    }

    @Test
    fun `missing CLOSED closureId fails safely`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED, closureId = null)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.printClosingTicket(day)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("No se pudo identificar el cierre para imprimir.", viewModel.printMessage.value)
        assertEquals(false, viewModel.isPrinting.value)
    }

}

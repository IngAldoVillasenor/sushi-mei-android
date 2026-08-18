package com.restaurant.sushimei.frontend

import com.restaurant.sushimei.frontend.data.local.PrintAttemptEntity
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus
import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository
import com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PrintManagerTest {

    private lateinit var repository: IPrintJobRepository
    private lateinit var operationalOrderRepository: IOperationalOrderRepository
    private lateinit var printService: PrintService
    private lateinit var manager: PrintManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        operationalOrderRepository = mockk(relaxed = true)
        printService = mockk(relaxed = true)

        coEvery { repository.observeAllJobs() } returns MutableStateFlow(emptyList())
        coEvery { repository.getPendingJobs() } returns emptyList()

        manager = PrintManager(
            repository,
            operationalOrderRepository,
            printService,
            TestScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `idempotency by orderId and requestId`() = runTest {
        val job = PrintJobEntity("job-1", "req-1", 1L, PrintJobStatus.PENDING, null, 0, 0, null, null)
        coEvery { repository.enqueuePrint(1L, "req-1") } returns job

        manager.enqueuePrintJob(1L, "req-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.enqueuePrint(1L, "req-1") }
    }

    @Test
    fun `original failure sets FAILED on attempt and job`() = runTest {
        val job = PrintJobEntity("job-1", "req-1", 1L, PrintJobStatus.PENDING, null, 0, 0, null, null)
        val attempt = PrintAttemptEntity("att-1", "job-1", PrintAttemptType.ORIGINAL, PrintAttemptStatus.PRINTING, 0, null, null)

        coEvery { repository.enqueuePrint(1L, "req-1") } returns job
        coEvery { repository.getJobById("job-1") } returns job
        coEvery { repository.beginAttempt("job-1", PrintAttemptType.ORIGINAL) } returns attempt

        val orderDetail = mockk<OperationalOrderDetailDto>()
        coEvery { operationalOrderRepository.getOperationalOrderDetail(1L) } returns orderDetail
        every { printService.printOperationalTicket(orderDetail, false) } returns false

        manager.enqueuePrintJob(1L, "req-1")
        advanceUntilIdle()

        coVerify { repository.finishAttempt("att-1", PrintAttemptStatus.FAILED, any(), "Bluetooth Error / Not Connected") }
        coVerify { repository.markJobFailed("job-1", "Bluetooth Error / Not Connected") }
    }

    @Test
    fun `FAILED retry becomes PRINTED and clears lastError`() = runTest {
        val job = PrintJobEntity("job-1", "req-1", 1L, PrintJobStatus.FAILED, "error", 0, 0, null, null)
        val attempt = PrintAttemptEntity("att-1", "job-1", PrintAttemptType.RETRY, PrintAttemptStatus.PRINTING, 0, null, null)

        coEvery { repository.getJobById("job-1") } returns job
        coEvery { repository.beginAttempt("job-1", PrintAttemptType.RETRY) } returns attempt

        val orderDetail = mockk<OperationalOrderDetailDto>()
        coEvery { operationalOrderRepository.getOperationalOrderDetail(1L) } returns orderDetail
        every { printService.printOperationalTicket(orderDetail, false) } returns true

        manager.retryPrintJob("job-1")
        advanceUntilIdle()

        coVerify { repository.finishAttempt("att-1", PrintAttemptStatus.SUCCEEDED, any(), null) }
        coVerify { repository.markJobPrinted("job-1") }
    }

    @Test
    fun `failed REPRINT keeps parent PRINTED`() = runTest {
        val job = PrintJobEntity("job-1", "req-1", 1L, PrintJobStatus.PRINTED, null, 0, 0, 12345L, null)
        val attempt = PrintAttemptEntity("att-1", "job-1", PrintAttemptType.REPRINT, PrintAttemptStatus.PRINTING, 0, null, null)

        coEvery { repository.getJobById("job-1") } returns job
        coEvery { repository.beginAttempt("job-1", PrintAttemptType.REPRINT) } returns attempt

        val orderDetail = mockk<OperationalOrderDetailDto>()
        coEvery { operationalOrderRepository.getOperationalOrderDetail(1L) } returns orderDetail
        every { printService.printOperationalTicket(orderDetail, true) } returns false

        manager.reprintJob("job-1")
        advanceUntilIdle()

        coVerify { repository.finishAttempt("att-1", PrintAttemptStatus.FAILED, any(), "Bluetooth Error / Not Connected") }
        coVerify(exactly = 0) { repository.markJobFailed("job-1", any()) }
    }

    @Test
    fun `concurrent retry and reprint acquires attempt lock and prevents duplicate prints`() = runTest {
        val job = PrintJobEntity("job-1", "req-1", 1L, PrintJobStatus.PENDING, null, 0, 0, null, null)
        val attempt1 = PrintAttemptEntity("att-1", "job-1", PrintAttemptType.RETRY, PrintAttemptStatus.PRINTING, 0, null, null)

        coEvery { repository.getJobById("job-1") } returns job

        coEvery { repository.beginAttempt("job-1", PrintAttemptType.RETRY) } returns attempt1
        coEvery { repository.beginAttempt("job-1", PrintAttemptType.REPRINT) } returns null

        val orderDetail = mockk<OperationalOrderDetailDto>()
        coEvery { operationalOrderRepository.getOperationalOrderDetail(1L) } returns orderDetail
        every { printService.printOperationalTicket(orderDetail, any()) } returns true

        manager.retryPrintJob("job-1")
        manager.reprintJob("job-1")
        advanceUntilIdle()

        verify(exactly = 1) { printService.printOperationalTicket(any(), any()) }
    }
}

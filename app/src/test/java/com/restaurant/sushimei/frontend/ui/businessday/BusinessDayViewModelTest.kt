package com.restaurant.sushimei.frontend.ui.businessday

import com.restaurant.sushimei.frontend.PrintManager
import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.BusinessDayStatus
import com.restaurant.sushimei.frontend.data.model.CashExpenseCreateResponse
import com.restaurant.sushimei.frontend.data.model.CashExpenseDto
import com.restaurant.sushimei.frontend.data.model.CashExpenseRequest
import com.restaurant.sushimei.frontend.data.model.CashExpenseResult
import com.restaurant.sushimei.frontend.data.model.PrintDocumentType
import com.restaurant.sushimei.frontend.data.local.PrintJobEntity
import com.restaurant.sushimei.frontend.data.model.PrintJobStatus
import com.restaurant.sushimei.frontend.data.repository.IBusinessDayRepository
import com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessDayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: IBusinessDayRepository
    private lateinit var printManager: PrintManager
    private lateinit var printJobRepository: IPrintJobRepository
    private lateinit var viewModel: BusinessDayViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        printManager = mockk()
        printJobRepository = mockk()

        coEvery { repository.getCashExpenses() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDay(status: BusinessDayStatus, closureId: Long? = null, businessDayId: Long = 1L): BusinessDayResponse {
        return BusinessDayResponse(
            businessDayId = businessDayId, businessDate = "2026-08-31", status = status,
            openingCashAmount = BigDecimal.TEN, openedAt = Instant.now(), openedByUserId = 1L,
            closedAt = if (status == BusinessDayStatus.CLOSED) Instant.now() else null,
            closedByUserId = if (status == BusinessDayStatus.CLOSED) 2L else null,
            cashSalesAmount = BigDecimal.ZERO, transferSalesAmount = BigDecimal.ZERO, cardSalesAmount = BigDecimal.ZERO, unclassifiedSalesAmount = BigDecimal.ZERO,
            cashDifferenceAmount = BigDecimal.ZERO, expectedClosingCashAmount = BigDecimal.ZERO, actualClosingCashAmount = BigDecimal.ZERO, completedSalesAmount = BigDecimal.ZERO, completedOrderCount = 0, voidedOrderCount = 0,
            closureId = closureId, closureNumber = null, cashExpenseAmount = BigDecimal.ZERO, cashExpenseCount = 0
        )
    }

    private fun createJob(status: PrintJobStatus): PrintJobEntity {
        return PrintJobEntity(id = "1", requestId = "req", documentType = PrintDocumentType.BUSINESS_DAY_CLOSE, documentId = 456L, snapshotPayload = null, status = status, lastError = if (status == PrintJobStatus.FAILED) "Test Error" else null, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), printedAt = null)
    }

    // --- Legacy Tests ---
    @Test
    fun `closing print enqueues correct documentType and documentId`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED, closureId = 456L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns createJob(PrintJobStatus.PENDING)
        val flow = MutableStateFlow<PrintJobEntity?>(null)
        every { printJobRepository.observeJobById(any()) } returns flow

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.printClosingTicket(day)
        advanceUntilIdle()

        coVerify { printManager.enqueuePrintJob(PrintDocumentType.BUSINESS_DAY_CLOSE, 456L, "close-456", any()) }
    }

    @Test
    fun `print FAILED reaches the expected UI state`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED, closureId = 456L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns createJob(PrintJobStatus.FAILED)
        val flow = MutableStateFlow<PrintJobEntity?>(createJob(PrintJobStatus.FAILED))
        every { printJobRepository.observeJobById(any()) } returns flow

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.printClosingTicket(day)
        advanceUntilIdle()

        assertTrue(viewModel.printMessage.value?.contains("No se pudo imprimir") == true)
    }

    @Test
    fun `print PRINTED reaches the expected UI state`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED, closureId = 456L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { printManager.enqueuePrintJob(any(), any(), any(), any()) } returns createJob(PrintJobStatus.PRINTED)
        val flow = MutableStateFlow<PrintJobEntity?>(createJob(PrintJobStatus.PRINTED))
        every { printJobRepository.observeJobById(any()) } returns flow

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.printClosingTicket(day)
        advanceUntilIdle()

        assertEquals("Cierre impreso correctamente", viewModel.printMessage.value)
    }

    @Test
    fun `BUSINESS_DAY_HAS_ACTIVE_ORDERS preserves the useful backend UI message`() = runTest(testDispatcher) {
        val exception = ApiException("BUSINESS_DAY_HAS_ACTIVE_ORDERS", "Orders active", 400)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(createDay(BusinessDayStatus.OPEN))
        coEvery { repository.closeBusinessDay(any()) } returns Result.failure(exception)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.closeBusinessDay(BigDecimal.ZERO)
        advanceUntilIdle()

        val st = viewModel.state.value as BusinessDayState.Error
        assertEquals("Orders active", st.message)
    }

    @Test
    fun `reopen success transitions to Open and exposes success message`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val openDay = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.success(openDay)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.reopenBusinessDay()
        advanceUntilIdle()

        assertEquals("Día reabierto correctamente", viewModel.reopenMessage.value)
        assertTrue(viewModel.state.value is BusinessDayState.Open)
    }

    @Test
    fun `BUSINESS_DAY_REOPEN_NOT_ALLOWED preserves Closed state and useful message`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.failure(ApiException("BUSINESS_DAY_REOPEN_NOT_ALLOWED", "Not allowed", 403))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.reopenBusinessDay()
        advanceUntilIdle()

        assertEquals("No está permitido reabrir el día.", viewModel.reopenMessage.value)
        assertTrue(viewModel.state.value is BusinessDayState.Closed)
    }

    @Test
    fun `BUSINESS_DAY_NOT_CLOSED preserves Closed state and useful message`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.failure(ApiException("BUSINESS_DAY_NOT_CLOSED", "Not closed", 400))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.reopenBusinessDay()
        advanceUntilIdle()

        assertEquals("El día no está cerrado.", viewModel.reopenMessage.value)
        assertTrue(viewModel.state.value is BusinessDayState.Closed)
    }

    @Test
    fun `missing CLOSED closureId fails safely without enqueueing invalid print work`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED, closureId = null)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.printClosingTicket(day)
        advanceUntilIdle()

        assertEquals("No se pudo identificar el cierre para imprimir.", viewModel.printMessage.value)
        coVerify(exactly = 0) { printManager.enqueuePrintJob(any(), any(), any(), any()) }
    }


    // --- Cash Expense V3 Tests ---

    @Test
    fun `1 Repository GET success loads expenses`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("10.00"), description = "Desc", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val state = viewModel.expensesState.value
        assertTrue(state is CashExpensesState.Loaded)
        assertEquals(1, (state as CashExpensesState.Loaded).expenses.size)
    }

    @Test
    fun `2 POST 201 CREATED clears pending and refreshes`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("10.00"), description = "Desc", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))
        coEvery { repository.createCashExpense(any()) } returns Result.success(CashExpenseCreateResponse(exp, CashExpenseResult.CREATED))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        assertTrue(viewModel.expenseSubmittedSuccessfully.value)
    }

    @Test
    fun `3 POST 200 ALREADY_CREATED clears pending and refreshes`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("10.00"), description = "Desc", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))
        coEvery { repository.createCashExpense(any()) } returns Result.success(CashExpenseCreateResponse(exp, CashExpenseResult.ALREADY_CREATED))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        assertTrue(viewModel.expenseSubmittedSuccessfully.value)
    }

    @Test
    fun `4 Exact same failed payload reuses same UUID`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { repository.createCashExpense(any()) } returns Result.failure(Exception("Network"))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))

        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))

        viewModel.submitCashExpense(BigDecimal("10"), " Desc ", "")
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertEquals(id1, id2)
    }

    @Test
    fun `5 Changed amount uses NEW UUID`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("15.00"), "Desc", null)
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertFalse(id1 == id2)
    }

    @Test
    fun `6 Changed description uses NEW UUID`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "New Desc", null)
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertFalse(id1 == id2)
    }

    @Test
    fun `7 Changed note uses NEW UUID`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", "A")
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertFalse(id1 == id2)
    }

    @Test
    fun `8 Explicit abandon clears pending request`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        viewModel.abandonPendingExpenseSubmission()

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertFalse(id1 == id2)
    }

    @Test
    fun `9 Hide message preserves pending request`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        viewModel.clearExpenseMessageState()

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertEquals(id1, id2)
    }

    @Test
    fun `10 Idempotency conflict does not auto retry`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val ex = ApiException("BUSINESS_DAY_CASH_EXPENSE_IDEMPOTENCY_CONFLICT", "Conflict", 409)
        coEvery { repository.createCashExpense(any()) } returns Result.failure(ex)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        assertEquals("Conflicto de idempotencia. Revise los datos e intente de nuevo.", viewModel.expenseSubmitError.value)
        coVerify(exactly = 1) { repository.createCashExpense(any()) }
    }

    @Test
    fun `11 Double submit is exactly one repo create invocation`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("10.00"), description = "Desc", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))
        coEvery { repository.createCashExpense(any()) } coAnswers { delay(100); Result.success(CashExpenseCreateResponse(exp, CashExpenseResult.CREATED)) }

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.createCashExpense(any()) }
    }

    @Test
    fun `12 Success clears pending request`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("10.00"), description = "Desc", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))
        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.success(CashExpenseCreateResponse(exp, CashExpenseResult.CREATED))

        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.success(CashExpenseCreateResponse(exp, CashExpenseResult.CREATED))

        viewModel.clearExpenseMessageState()
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertFalse(id1 == id2)
    }

    @Test
    fun `13 Success does not duplicate rows in UI`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("10.00"), description = "Desc", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))
        coEvery { repository.createCashExpense(any()) } returns Result.success(CashExpenseCreateResponse(exp, CashExpenseResult.ALREADY_CREATED))

        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        val state = viewModel.expensesState.value as CashExpensesState.Loaded
        assertEquals(1, state.expenses.size)
    }

    @Test
    fun `14 Failed creation preserves list`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        val exp = CashExpenseDto(id = 1L, businessDayId = 1L, requestId = UUID.randomUUID(), amount = BigDecimal("5.00"), description = "Old", note = null, createdAt = Instant.now(), createdByUserId = 1L)
        coEvery { repository.getCashExpenses() } returns Result.success(listOf(exp))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        coEvery { repository.createCashExpense(any()) } returns Result.failure(Exception("Net error"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        val state = viewModel.expensesState.value as CashExpensesState.Loaded
        assertEquals(1, state.expenses.size)
    }

    @Test
    fun `15 GET failure is Error`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { repository.getCashExpenses() } returns Result.failure(Exception("Load error"))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val state = viewModel.expensesState.value
        assertTrue(state is CashExpensesState.Error)
    }

    @Test
    fun `16 Loaded empty list is Loaded`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val state = viewModel.expensesState.value
        assertTrue(state is CashExpensesState.Loaded)
        assertEquals(0, (state as CashExpensesState.Loaded).expenses.size)
    }

    @Test
    fun `17 Retry after GET error succeeds`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        coEvery { repository.getCashExpenses() } returns Result.failure(Exception("Load error"))

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        coEvery { repository.getCashExpenses() } returns Result.success(emptyList())
        viewModel.loadCashExpenses(1L)
        advanceUntilIdle()

        assertTrue(viewModel.expensesState.value is CashExpensesState.Loaded)
    }

    @Test
    fun `18 NotOpen clears expense state`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        coEvery { repository.getCurrentBusinessDay() } returns Result.success(null)
        viewModel.loadCurrentBusinessDay()
        advanceUntilIdle()

        assertTrue(viewModel.expensesState.value is CashExpensesState.Idle)
    }

    @Test
    fun `19 Closed discovered clears expense state`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        coEvery { repository.getCurrentBusinessDay() } returns Result.success(createDay(BusinessDayStatus.CLOSED))
        viewModel.loadCurrentBusinessDay()
        advanceUntilIdle()

        assertTrue(viewModel.expensesState.value is CashExpensesState.Idle)
    }

    @Test
    fun `20 Reopen loads expenses`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val openDay = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.reopenCurrentBusinessDay() } returns Result.success(openDay)
        viewModel.reopenBusinessDay()
        advanceUntilIdle()

        assertTrue(viewModel.expensesState.value is CashExpensesState.Loaded)
    }

    @Test
    fun `21 Submitting not OPEN makes zero calls`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.CLOSED)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.createCashExpense(any()) }
    }

    @Test
    fun `22 amount zero rejected`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("0"), "Desc", null)
        advanceUntilIdle()

        assertEquals("Datos inválidos.", viewModel.expenseSubmitError.value)
    }

    @Test
    fun `23 amount negative rejected`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("-10.00"), "Desc", null)
        advanceUntilIdle()

        assertEquals("Datos inválidos.", viewModel.expenseSubmitError.value)
    }

    @Test
    fun `24 amount with 3 fractional digits rejected`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("10.005"), "Desc", null)
        advanceUntilIdle()

        assertEquals("Datos inválidos.", viewModel.expenseSubmitError.value)
    }

    @Test
    fun `25 blank description rejected`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("10.00"), "   ", null)
        advanceUntilIdle()

        assertEquals("Datos inválidos.", viewModel.expenseSubmitError.value)
    }

    @Test
    fun `26 description over 500 rejected`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("10.00"), "a".repeat(501), null)
        advanceUntilIdle()

        assertEquals("Datos inválidos.", viewModel.expenseSubmitError.value)
    }

    @Test
    fun `27 note over 500 rejected`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", "a".repeat(501))
        advanceUntilIdle()

        assertEquals("Datos inválidos.", viewModel.expenseSubmitError.value)
    }

    @Test
    fun `28 different businessDayId creates new UUID`() = runTest(testDispatcher) {
        val day1 = createDay(BusinessDayStatus.OPEN, businessDayId = 1L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day1)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        val slot1 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot1)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id1 = slot1.captured.requestId

        val day2 = createDay(BusinessDayStatus.OPEN, businessDayId = 2L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day2)
        viewModel.loadCurrentBusinessDay()
        advanceUntilIdle()

        val slot2 = slot<CashExpenseRequest>()
        coEvery { repository.createCashExpense(capture(slot2)) } returns Result.failure(Exception("Network"))
        viewModel.submitCashExpense(BigDecimal("10.00"), "Desc", null)
        advanceUntilIdle()
        val id2 = slot2.captured.requestId

        assertFalse(id1 == id2)
    }

    @Test
    fun `29 same-day stale GET is ignored`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        // Setup multiple delays
        coEvery { repository.getCashExpenses() } coAnswers {
            delay(100) // First load is slow
            Result.success(emptyList())
        }
        viewModel.loadCashExpenses(day.businessDayId)

        coEvery { repository.getCashExpenses() } coAnswers {
            Result.success(listOf(CashExpenseDto(1L, 1L, UUID.randomUUID(), BigDecimal.TEN, "Late", null, Instant.now(), 1L)))
        }
        viewModel.loadCashExpenses(day.businessDayId) // Second load is fast
        advanceUntilIdle()

        // Wait for first load to finish (delay passed)
        advanceUntilIdle()

        val state = viewModel.expensesState.value as CashExpensesState.Loaded
        assertEquals(1, state.expenses.size) // The slow one should not overwrite the fast one with emptyList()
    }

    @Test
    fun `30 successful GET clears cashExpensesMessage`() = runTest(testDispatcher) {
        val day = createDay(BusinessDayStatus.OPEN)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day)

        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        coEvery { repository.getCashExpenses() } returns Result.failure(Exception("Error"))
        viewModel.loadCashExpenses(day.businessDayId)
        advanceUntilIdle()

        assertEquals("Error al actualizar la lista de egresos.", viewModel.cashExpensesMessage.value)

        coEvery { repository.getCashExpenses() } returns Result.success(emptyList())
        viewModel.loadCashExpenses(day.businessDayId)
        advanceUntilIdle()

        assertEquals(null, viewModel.cashExpensesMessage.value)
    }

    @Test
    fun `31 different businessDay stale GET is ignored`() = runTest(testDispatcher) {
        val day1 = createDay(BusinessDayStatus.OPEN, businessDayId = 1L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day1)
        viewModel = BusinessDayViewModel(repository, printManager, printJobRepository)
        advanceUntilIdle()

        // Start a load for day 1 that is delayed.
        coEvery { repository.getCashExpenses() } coAnswers {
            delay(100)
            Result.success(listOf(CashExpenseDto(1L, 1L, java.util.UUID.randomUUID(), java.math.BigDecimal.TEN, "Old", null, java.time.Instant.now(), 1L)))
        }
        viewModel.loadCashExpenses(1L)
        runCurrent()

        // Before the delay finishes, change the day and start another load.
        val day2 = createDay(BusinessDayStatus.OPEN, businessDayId = 2L)
        coEvery { repository.getCurrentBusinessDay() } returns Result.success(day2)
        coEvery { repository.getCashExpenses() } returns Result.success(emptyList())
        viewModel.loadCurrentBusinessDay()
        runCurrent()

        advanceUntilIdle()

        val state = viewModel.expensesState.value
        assertTrue(state is CashExpensesState.Loaded && state.expenses.isEmpty())
    }
}

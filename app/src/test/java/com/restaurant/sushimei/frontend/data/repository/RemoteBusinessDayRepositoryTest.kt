package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RemoteBusinessDayRepositoryTest {

    private lateinit var classUnderTest: RemoteBusinessDayRepository
    private lateinit var api: SushiMeiApi

    @Before
    fun setUp() {
        api = mockk()
        classUnderTest = RemoteBusinessDayRepository(api)
    }

    @Test
    fun `getCashExpenses returns successful list`() = runTest {
        val expense = CashExpenseDto(
            id = 1L,
            businessDayId = 2L,
            requestId = UUID.randomUUID(),
            amount = BigDecimal("10.00"),
            description = "Test",
            note = null,
            createdAt = Instant.now(),
            createdByUserId = 1L
        )
        coEvery { api.getCashExpenses() } returns Response.success(listOf(expense))

        val result = classUnderTest.getCashExpenses()
        assertTrue(result.isSuccess)
        assertEquals(listOf(expense), result.getOrNull())
    }

    @Test
    fun `createCashExpense handles CREATED`() = runTest {
        val request = CashExpenseRequest(UUID.randomUUID(), BigDecimal("10.00"), "Test", null)
        val expense = CashExpenseDto(
            id = 1L,
            businessDayId = 2L,
            requestId = request.requestId,
            amount = BigDecimal("10.00"),
            description = "Test",
            note = null,
            createdAt = Instant.now(),
            createdByUserId = 1L
        )
        val responseBody = CashExpenseCreateResponse(expense, CashExpenseResult.CREATED)

        coEvery { api.createCashExpense(request) } returns Response.success(201, responseBody)

        val result = classUnderTest.createCashExpense(request)
        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull())
    }

    @Test
    fun `createCashExpense handles ALREADY_CREATED`() = runTest {
        val request = CashExpenseRequest(UUID.randomUUID(), BigDecimal("10.00"), "Test", null)
        val expense = CashExpenseDto(
            id = 1L,
            businessDayId = 2L,
            requestId = request.requestId,
            amount = BigDecimal("10.00"),
            description = "Test",
            note = null,
            createdAt = Instant.now(),
            createdByUserId = 1L
        )
        val responseBody = CashExpenseCreateResponse(expense, CashExpenseResult.ALREADY_CREATED)

        coEvery { api.createCashExpense(request) } returns Response.success(200, responseBody)

        val result = classUnderTest.createCashExpense(request)
        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull())
    }
}

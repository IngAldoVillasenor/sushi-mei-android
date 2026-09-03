package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteOperationalOrderRepositoryTest {

    private lateinit var api: SushiMeiApi
    private lateinit var repository: RemoteOperationalOrderRepository

    @Before
    fun setup() {
        api = mockk()
        repository = RemoteOperationalOrderRepository(api)
    }

    @Test
    fun `HTTP 502 maps to IOException`() = runTest {
        val errorResponse = Response.error<com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse>(502, "Bad Gateway".toResponseBody("text/plain".toMediaTypeOrNull()))
        coEvery { api.collectPayment(any(), any()) } returns errorResponse

        try {
            repository.collectPayment(1L, PaymentMethod.CASH, null)
            fail("Expected IOException")
        } catch (e: Exception) {
            assertTrue(e is IOException)
            assertEquals("Gateway Error: 502", e.message)
        }
    }

    @Test
    fun `HTTP 503 maps to IOException`() = runTest {
        val errorResponse = Response.error<com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse>(503, "Service Unavailable".toResponseBody("text/plain".toMediaTypeOrNull()))
        coEvery { api.collectPayment(any(), any()) } returns errorResponse

        try {
            repository.collectPayment(1L, PaymentMethod.CASH, null)
            fail("Expected IOException")
        } catch (e: Exception) {
            assertTrue(e is IOException)
            assertEquals("Gateway Error: 503", e.message)
        }
    }

    @Test
    fun `HTTP 504 maps to IOException`() = runTest {
        val errorResponse = Response.error<com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse>(504, "Gateway Timeout".toResponseBody("text/plain".toMediaTypeOrNull()))
        coEvery { api.collectPayment(any(), any()) } returns errorResponse

        try {
            repository.collectPayment(1L, PaymentMethod.CASH, null)
            fail("Expected IOException")
        } catch (e: Exception) {
            assertTrue(e is IOException)
            assertEquals("Gateway Error: 504", e.message)
        }
    }

    @Test
    fun `structured 4xx maps to ApiException`() = runTest {
        val json = """{"code":"BUSINESS_ERROR", "message":"Some message"}"""
        val errorResponse = Response.error<com.restaurant.sushimei.frontend.data.model.OrderPaymentCollectionResponse>(400, json.toResponseBody("application/json".toMediaTypeOrNull()))
        coEvery { api.collectPayment(any(), any()) } returns errorResponse

        try {
            repository.collectPayment(1L, PaymentMethod.CASH, null)
            fail("Expected ApiException")
        } catch (e: Exception) {
            assertTrue(e is ApiException)
            
            
        }
    }
}

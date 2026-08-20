package com.restaurant.sushimei.frontend.data.api

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ApiErrorInterceptorTest {

    private lateinit var diagnosticsLogger: DiagnosticsLogger
    private lateinit var interceptor: ApiErrorInterceptor

    @Before
    fun setup() {
        diagnosticsLogger = object : DiagnosticsLogger {
            override fun error(event: String, fields: Map<String, Any?>) {}
            override fun debug(event: String, fields: Map<String, Any?>) {}

        }
        interceptor = ApiErrorInterceptor(Gson(), diagnosticsLogger)
    }

    private fun executeIntercept(code: Int, bodyStr: String) {
        val request = Request.Builder().url("http://localhost/").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Server Error")
            .body(bodyStr.toResponseBody())
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response

        interceptor.intercept(chain)
    }

    @Test
    fun `intercept handles 500 JSON with no code`() {
        var caught: ApiException? = null
        try {
            executeIntercept(500, """{"message": "Internal Server Error"}""")
        } catch (e: ApiException) {
            caught = e
        }

        assertTrue(caught != null)
        assertEquals("UNKNOWN_ERROR", caught?.code)
        assertEquals("Internal Server Error", caught?.message)
    }

    @Test
    fun `intercept handles 500 JSON with no message`() {
        var caught: ApiException? = null
        try {
            executeIntercept(500, """{"code": "SOME_CODE"}""")
        } catch (e: ApiException) {
            caught = e
        }

        assertTrue(caught != null)
        assertEquals("SOME_CODE", caught?.code)
        assertEquals("Error HTTP 500", caught?.message)
    }

    @Test
    fun `intercept handles 500 empty body`() {
        var caught: ApiException? = null
        try {
            executeIntercept(500, "")
        } catch (e: ApiException) {
            caught = e
        }

        assertTrue(caught != null)
        assertEquals("UNKNOWN_ERROR", caught?.code)
        assertEquals("Error HTTP 500", caught?.message)
    }

    @Test
    fun `intercept handles 500 malformed JSON body`() {
        var caught: ApiException? = null
        try {
            executeIntercept(500, "<html><body>Error</body></html>")
        } catch (e: ApiException) {
            caught = e
        }

        assertTrue(caught != null)
        assertEquals("UNKNOWN_ERROR", caught?.code)
        assertEquals("Error HTTP 500", caught?.message)
    }

    @Test
    fun `intercept known VERSION_CONFLICT still maps correctly`() {
        var caught: Exception? = null
        try {
            executeIntercept(409, """{"code": "VERSION_CONFLICT", "message": "Conflict"}""")
        } catch (e: Exception) {
            caught = e
        }

        assertTrue(caught is VersionConflictException)
        assertEquals("Conflict", caught?.message)
    }
}

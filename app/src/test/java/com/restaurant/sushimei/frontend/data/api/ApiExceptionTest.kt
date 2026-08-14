package com.restaurant.sushimei.frontend.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiExceptionTest {

    @Test
    fun `keeps HTTP and correlation metadata without exposing the full id in UI`() {
        val exception = ApiException(
            code = "PROMOTION_SCHEDULE_CONFLICT",
            message = "Las promociones se traslapan.",
            httpStatus = 409,
            requestId = "12345678-abcd-efab-cdef-1234567890ab"
        )

        assertEquals(409, exception.httpStatus)
        assertEquals("12345678-abcd-efab-cdef-1234567890ab", exception.requestId)
        assertEquals(" (Ref. 12345678)", exception.referenceSuffix())
    }
}

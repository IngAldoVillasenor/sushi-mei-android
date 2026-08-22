package com.restaurant.sushimei.frontend.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    @Test
    fun `formatCurrency handles positive currency`() {
        assertEquals("$1,500.00", formatCurrency(BigDecimal("1500")))
        assertEquals("$813.00", formatCurrency(BigDecimal("813")))
    }

    @Test
    fun `formatCurrency handles negative currency`() {
        assertEquals("-$13.00", formatCurrency(BigDecimal("-13")))
        assertEquals("-$1,500.00", formatCurrency(BigDecimal("-1500")))
    }

    @Test
    fun `formatCurrency handles zero`() {
        assertEquals("$0.00", formatCurrency(BigDecimal.ZERO))
    }

    @Test
    fun `formatCurrency handles nullable currency`() {
        assertEquals("N/A", formatCurrency(null))
    }
}

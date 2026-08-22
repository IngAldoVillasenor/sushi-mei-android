package com.restaurant.sushimei.frontend.ui.util

import java.math.BigDecimal
import java.util.Locale

fun formatCurrency(value: BigDecimal?): String {
    if (value == null) return "N/A"
    return if (value < BigDecimal.ZERO) {
        "-$" + String.format(Locale.US, "%,.2f", value.abs())
    } else {
        "$" + String.format(Locale.US, "%,.2f", value)
    }
}

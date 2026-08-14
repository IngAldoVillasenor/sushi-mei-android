package com.restaurant.sushimei.frontend.data.local

import android.content.Context

/** Evita reimprimir una venta confirmada durante reintentos idempotentes del POS. */
class PosTicketPrintTracker(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "pos_ticket_prints",
        Context.MODE_PRIVATE
    )

    fun wasPrinted(requestId: String): Boolean = preferences.getBoolean(requestId, false)

    fun markPrinted(requestId: String) {
        preferences.edit().putBoolean(requestId, true).apply()
    }
}

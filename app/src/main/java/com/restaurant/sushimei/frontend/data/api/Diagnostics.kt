package com.restaurant.sushimei.frontend.data.api

import android.util.Log

internal interface DiagnosticsLogger {
    fun debug(event: String, fields: Map<String, Any?>)
    fun error(event: String, fields: Map<String, Any?>)
}

internal object AndroidDiagnosticsLogger : DiagnosticsLogger {
    private const val TAG = "SushiMeiApi"

    override fun debug(event: String, fields: Map<String, Any?>) {
        Log.d(TAG, format(event, fields))
    }

    override fun error(event: String, fields: Map<String, Any?>) {
        Log.e(TAG, format(event, fields))
    }

    private fun format(event: String, fields: Map<String, Any?>): String = buildString {
        append(event)
        fields.forEach { (key, value) ->
            append(' ')
            append(key)
            append('=')
            append(value ?: "none")
        }
    }
}

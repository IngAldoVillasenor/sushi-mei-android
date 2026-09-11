package com.cardovia.merkon.app.util

import java.net.URI

object VerificationTokenParser {

    private val RAW_TOKEN_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    fun parse(input: String, expectedHost: String): String? {
        if (input.isBlank()) return null

        val trimmed = input.trim()

        if (RAW_TOKEN_REGEX.matches(trimmed)) {
            if (trimmed.length > 512) return null
            return trimmed
        }

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return null
        }

        if (uri.scheme?.lowercase() != "https") return null
        if (uri.host?.lowercase() != expectedHost.lowercase()) return null
        if (uri.path != "/verify-email") return null
        if (uri.port != -1) return null

        val query = uri.query ?: return null
        val params = query.split("&").associate {
            val parts = it.split("=")
            parts[0] to if (parts.size > 1) parts[1] else ""
        }
        
        val token = params["token"]
        if (token.isNullOrBlank()) return null
        if (token.length > 512) return null
        if (!RAW_TOKEN_REGEX.matches(token)) return null
        return token
    }
}

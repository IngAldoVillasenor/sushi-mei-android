package com.cardovia.merkon.app.util

object PasswordResetTokenParser {
    fun parse(input: String, expectedHost: String): String? {
        return TokenParser.parse(input, expectedHost, "/reset-password")
    }
}

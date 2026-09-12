package com.cardovia.merkon.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordResetTokenParserTest {
    @Test
    fun parse_validUrl_returnsToken() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/reset-password?token=abc-123", "reset.invalid")
        assertEquals("abc-123", result)
    }

    @Test
    fun parse_verificationUrl_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/verify-email?token=abc-123", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_wrongHost_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://wrong.host/reset-password?token=abc-123", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_missingToken_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/reset-password", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_http_returnsNull() {
        val result = PasswordResetTokenParser.parse("http://reset.invalid/reset-password?token=abc", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_explicitPort_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid:443/reset-password?token=abc", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_rawToken_returnsToken() {
        val result = PasswordResetTokenParser.parse("abc-123", "reset.invalid")
        assertEquals("abc-123", result)
    }

    @Test
    fun parse_tooLong_returnsNull() {
        val longToken = "a".repeat(513)
        val result = PasswordResetTokenParser.parse("https://reset.invalid/reset-password?token=$longToken", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_anotherWrongPath_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/something-else?token=abc-123", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_blankToken_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/reset-password?token=   ", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_malformedTokenShape_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/reset-password?token=abc@123", "reset.invalid")
        assertNull(result)
    }

    @Test
    fun parse_malformedUrl_returnsNull() {
        val result = PasswordResetTokenParser.parse("https://reset.invalid/reset-password?token=abc-123:%0A malformed", "reset.invalid")
        assertNull(result)
    }
}

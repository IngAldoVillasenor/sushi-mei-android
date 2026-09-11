package com.cardovia.merkon.app.util

import org.junit.Assert.*
import org.junit.Test

class VerificationTokenParserTest {

    private val host = "verification.example.com"

    @Test
    fun parse_validRawToken_returnsToken() {
        val token = "abc-123_xyz"
        assertEquals(token, VerificationTokenParser.parse(token, host))
    }

    @Test
    fun parse_validHttpsUrl_returnsToken() {
        val url = "https://$host/verify-email?token=abc-123"
        assertEquals("abc-123", VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_httpUrl_returnsNull() {
        val url = "http://$host/verify-email?token=abc-123"
        assertNull(VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_wrongHost_returnsNull() {
        val url = "https://wrong.host/verify-email?token=abc-123"
        assertNull(VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_wrongPath_returnsNull() {
        val url = "https://$host/wrong-path?token=abc-123"
        assertNull(VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_missingToken_returnsNull() {
        val url = "https://$host/verify-email?notoken=abc-123"
        assertNull(VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_blankToken_returnsNull() {
        val url = "https://$host/verify-email?token="
        assertNull(VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_tokenTooLong_returnsNull() {
        val longToken = "a".repeat(513)
        val url = "https://$host/verify-email?token=$longToken"
        assertNull(VerificationTokenParser.parse(url, host))
        assertNull(VerificationTokenParser.parse(longToken, host))
    }

    @Test
    fun parse_uriLookingUnsupportedScheme_returnsNull() {
        val url = "merkon://$host/verify-email?token=abc-123"
        assertNull(VerificationTokenParser.parse(url, host))
    }

    @Test
    fun parse_rawTokenInvalidShape_returnsNull() {
        val badToken = "abc@123"
        assertNull(VerificationTokenParser.parse(badToken, host))
    }

    @Test
    fun parse_explicitPort_returnsNull() {
        val url = "https://$host:443/verify-email?token=abc-123"
        assertNull(VerificationTokenParser.parse(url, host))
    }
}

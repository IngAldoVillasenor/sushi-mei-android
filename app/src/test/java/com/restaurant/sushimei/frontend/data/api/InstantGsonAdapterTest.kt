package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.MenuItemResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class InstantGsonAdapterTest {

    private val gson = NetworkModule.configuredGson

    @Test
    fun testInstantDeserialization() {
        val json = """
            {
                "id": 1,
                "name": "Test Roll",
                "description": "A test roll",
                "category": "Rolls",
                "price": 120.0,
                "active": true,
                "available": true,
                "standaloneOrderable": true,
                "displayOrder": 1,
                "tags": [],
                "version": 1,
                "createdAt": "2026-08-11T23:59:01.123Z",
                "updatedAt": "2026-08-12T00:00:02Z"
            }
        """.trimIndent()

        val response = gson.fromJson(json, MenuItemResponse::class.java)
        assertEquals(Instant.parse("2026-08-11T23:59:01.123Z"), response.createdAt)
        assertEquals(Instant.parse("2026-08-12T00:00:02Z"), response.updatedAt)
    }

    @Test
    fun testInstantSerialization() {
        val instant = Instant.parse("2026-08-11T23:59:01.123Z")
        val json = gson.toJson(instant)
        assertEquals("\"2026-08-11T23:59:01.123Z\"", json)
    }

    @Test
    fun testNullInstantDeserialization() {
        val json = "null"
        val instant = gson.fromJson(json, Instant::class.java)
        assertNull(instant)
    }

    @Test
    fun testNullInstantSerialization() {
        val instant: Instant? = null
        val json = gson.toJson(instant, Instant::class.java)
        assertEquals("null", json)
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun testInvalidFormatFailsDeserialization() {
        val json = "\"2026-08-11 23:59:01\"" // Not ISO-8601
        gson.fromJson(json, Instant::class.java)
    }
}

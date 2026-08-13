package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.ItemPricingMode
import com.restaurant.sushimei.frontend.data.model.MenuItemResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant

class RemoteMenuRepositoryTest {

    @Test
    fun `toDomain maps pricingMode correctly`() = runTest {
        val api = mockk<SushiMeiApi>()
        val repository = RemoteMenuRepository(api)

        val validResponse = MenuItemResponse(
            id = 1L, name = "Test", description = null, category = "Test",
            price = BigDecimal.ZERO, active = true, available = true,
            standaloneOrderable = true, requiresConfiguration = true,
            pricingMode = ItemPricingMode.SELECTION_SUM,
            displayOrder = 1, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        coEvery { api.getMenuItems(any()) } returns Response.success(listOf(validResponse))

        val products = repository.getProducts()
        assertEquals(1, products.size)
        assertEquals(ItemPricingMode.SELECTION_SUM, products[0].pricingMode)
    }

    @Test
    fun `toDomain maps requiresConfiguration true correctly`() = runTest {
        val api = mockk<SushiMeiApi>()
        val repository = RemoteMenuRepository(api)

        val validResponse = MenuItemResponse(
            id = 2L, name = "Test2", description = null, category = "Test",
            price = BigDecimal.ZERO, active = true, available = true,
            standaloneOrderable = true, requiresConfiguration = true,
            pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = 1, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        coEvery { api.getMenuItems(any()) } returns Response.success(listOf(validResponse))

        val products = repository.getProducts()
        assertEquals(1, products.size)
        assertTrue(products[0].requiresConfiguration)
    }

    @Test
    fun `toDomain maps requiresConfiguration false correctly`() = runTest {
        val api = mockk<SushiMeiApi>()
        val repository = RemoteMenuRepository(api)

        val validResponse = MenuItemResponse(
            id = 3L, name = "Test3", description = null, category = "Test",
            price = BigDecimal.ZERO, active = true, available = true,
            standaloneOrderable = true, requiresConfiguration = false,
            pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = 1, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        coEvery { api.getMenuItems(any()) } returns Response.success(listOf(validResponse))

        val products = repository.getProducts()
        assertEquals(1, products.size)
        assertFalse(products[0].requiresConfiguration)
    }

    @Test
    fun `toDomain fails loudly if requiresConfiguration is missing`() = runTest {
        val api = mockk<SushiMeiApi>()
        val repository = RemoteMenuRepository(api)

        val invalidResponse = MenuItemResponse(
            id = 4L, name = "Test", description = null, category = "Test",
            price = BigDecimal.ZERO, active = true, available = true,
            standaloneOrderable = true, requiresConfiguration = null, // MISSING
            pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = 1, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        coEvery { api.getMenuItems(any()) } returns Response.success(listOf(invalidResponse))

        var caught = false
        try {
            repository.getProducts()
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue("Expected IllegalArgumentException", caught)
    }

    @Test
    fun `toDomain fails loudly if pricingMode is missing`() = runTest {
        val api = mockk<SushiMeiApi>()
        val repository = RemoteMenuRepository(api)

        val invalidResponse = MenuItemResponse(
            id = 5L, name = "Test", description = null, category = "Test",
            price = BigDecimal.ZERO, active = true, available = true,
            standaloneOrderable = true, requiresConfiguration = false,
            pricingMode = null, // MISSING
            displayOrder = 1, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        coEvery { api.getMenuItems(any()) } returns Response.success(listOf(invalidResponse))

        var caught = false
        try {
            repository.getProducts()
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue("Expected IllegalArgumentException", caught)
    }
}

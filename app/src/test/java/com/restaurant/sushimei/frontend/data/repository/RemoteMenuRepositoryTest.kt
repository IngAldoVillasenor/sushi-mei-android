package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.ItemPricingMode
import com.restaurant.sushimei.frontend.data.model.MenuItemResponse
import com.restaurant.sushimei.frontend.data.model.toDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant

class RemoteMenuRepositoryTest {

    @Test
    fun `refreshCatalog explicitly sends includeInactive when requested`() = runTest {
        val api = mockk<SushiMeiApi>(relaxed = true)
        val repository = RemoteMenuRepository(api)

        coEvery { api.getMenuItems(any(), any()) } returns Response.success(emptyList())

        repository.refreshCatalog(standaloneOnly = null, includeInactive = true)

        coVerify(exactly = 1) { api.getMenuItems(standaloneOnly = null, includeInactive = true) }
    }

    @Test
    fun `getProducts defaults to active only`() = runTest {
        val api = mockk<SushiMeiApi>(relaxed = true)
        val repository = RemoteMenuRepository(api)

        coEvery { api.getMenuItems(any(), any()) } returns Response.success(emptyList())

        repository.getProducts()

        coVerify(exactly = 1) { api.getMenuItems(standaloneOnly = null, includeInactive = false) }
    }

    @Test
    fun `toDomain maps metadata correctly`() = runTest {
        val validResponse = MenuItemResponse(
            id = 1L, name = "Test", description = null, category = "Test",
            price = BigDecimal.ZERO, active = false, available = true,
            standaloneOrderable = false, requiresConfiguration = true,
            pricingMode = ItemPricingMode.SELECTION_SUM,
            displayOrder = 5, tags = emptyList(), version = 10L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        val domain = validResponse.toDomain()

        assertEquals(1L, domain.id)
        assertFalse(domain.activo)
        assertTrue(domain.available)
        assertFalse(domain.standaloneOrderable)
        assertTrue(domain.requiresConfiguration)
        assertEquals(ItemPricingMode.SELECTION_SUM, domain.pricingMode)
        assertEquals(5, domain.displayOrder)
        assertEquals(10L, domain.version)
    }

    @Test
    fun `observeActive excludes inactive items`() = runTest {
        val api = mockk<SushiMeiApi>(relaxed = true)
        val repository = RemoteMenuRepository(api)

        val activeResponse = MenuItemResponse(
            id = 1L, name = "Active", description = null, category = "Test",
            price = BigDecimal.ZERO, active = true, available = true,
            standaloneOrderable = true, requiresConfiguration = false,
            pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = 1, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val inactiveResponse = MenuItemResponse(
            id = 2L, name = "Inactive", description = null, category = "Test",
            price = BigDecimal.ZERO, active = false, available = true,
            standaloneOrderable = true, requiresConfiguration = false,
            pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = 2, tags = emptyList(), version = 1L,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        coEvery { api.getMenuItems(any(), any()) } returns Response.success(listOf(activeResponse, inactiveResponse))

        repository.refreshCatalog(includeInactive = true)

        val activeOnly = repository.observeActive().first()
        val all = repository.observeAll().first()

        assertEquals(2, all.size)
        assertEquals(1, activeOnly.size)
        assertEquals(1L, activeOnly[0].id)
    }
}

package com.restaurant.sushimei.frontend.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.restaurant.sushimei.frontend.data.model.CatalogTagDto
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.QuoteRequestDto
import com.restaurant.sushimei.frontend.data.model.QuoteResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación mock de [IMenuRepository] basada en `assets/menu.json`.
 *
 * Ya no es la implementación de producción — esa es [RoomMenuRepository].
 * Esta clase se conserva como referencia y para tests de integración que
 * no quieran levantar Room.
 *
 * Los métodos CRUD ([saveProduct], [setActive]) son no-operativos (no-op)
 * porque el JSON es de solo lectura. Para CRUD real usar [RoomMenuRepository].
 */
class MockMenuRepository(private val context: Context) : IMenuRepository {

    private val gson = Gson()
    private var cachedProducts: List<MenuItem>? = null

    // ── Lectura reactiva ─────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<MenuItem>> =
        flowOf(cachedProducts ?: emptyList())

    override fun observeActive(): Flow<List<MenuItem>> =
        flowOf(cachedProducts ?: emptyList())

    override fun observeActiveCategories(): Flow<List<String>> = observeActive().map { list ->
        list.map { it.categoria }.distinct().sorted()
    }

    override suspend fun refreshCatalog(standaloneOnly: Boolean?) {
        // No-op for mock
    }

    // ── Lectura puntual ──────────────────────────────────────────────────────

    override suspend fun getProducts(): List<MenuItem> = withContext(Dispatchers.IO) {
        cachedProducts ?: loadFromAssets().also { cachedProducts = it }
    }

    override suspend fun getCategories(): List<String> =
        getProducts().map { it.categoria }.distinct().sorted()

    // ── Escritura (no-op — JSON es de solo lectura) ──────────────────────────

    @android.annotation.SuppressLint("NewApi")
    override suspend fun createProduct(request: com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse {
        return com.restaurant.sushimei.frontend.data.model.MenuItemResponse(
            id = 1L, name = request.name, description = request.description, category = request.category,
            price = request.price,            active = true, available = request.available, standaloneOrderable = request.standaloneOrderable,
            requiresConfiguration = false, pricingMode = com.restaurant.sushimei.frontend.data.model.ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = request.displayOrder,
            tags = emptyList(), version = 1L, createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now()
        )
    }

    @android.annotation.SuppressLint("NewApi")
    override suspend fun updateProduct(id: Long, request: com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse {
        return com.restaurant.sushimei.frontend.data.model.MenuItemResponse(
            id = id, name = request.name, description = request.description, category = request.category,
            price = request.price,            active = request.active, available = request.available, standaloneOrderable = request.standaloneOrderable,
            requiresConfiguration = false, pricingMode = com.restaurant.sushimei.frontend.data.model.ItemPricingMode.BASE_PLUS_ADJUSTMENTS,
            displayOrder = request.displayOrder,
            tags = emptyList(), version = request.version, createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now()
        )
    }

    override suspend fun deleteProduct(id: Long) { /* no-op */ }

    override suspend fun setActive(id: Long, activo: Boolean) { /* no-op */ }

    override suspend fun getConfiguration(menuItemId: Long): com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto {
        return com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Producto Simple",
            standaloneOrderable = true,
            basePrice = java.math.BigDecimal.ZERO,
            requiresConfiguration = false
        )
    }

    override suspend fun quoteItem(menuItemId: Long, request: com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto): com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto {
        return com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto(
            menuItemId = menuItemId,
            name = "Item",
            quantity = request.quantity,
            baseUnitPrice = java.math.BigDecimal.ZERO,
            baseTotal = java.math.BigDecimal.ZERO,
            unitAdjustmentTotal = java.math.BigDecimal.ZERO,
            unitTotal = java.math.BigDecimal.ZERO,
            total = java.math.BigDecimal.ZERO
        )
    }

    override suspend fun getTags(): List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto> {
        return listOf(
            com.restaurant.sushimei.frontend.data.model.CatalogTagDto(1L, "ROLL", "Rollo", true, 1, 1L)
        )
    }

    override suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.TagCreateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto {
        return com.restaurant.sushimei.frontend.data.model.CatalogTagDto(1L, tag.code, tag.name, true, tag.displayOrder, 1L)
    }

    override suspend fun updateTag(id: Long, tag: com.restaurant.sushimei.frontend.data.model.TagUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto {
        return com.restaurant.sushimei.frontend.data.model.CatalogTagDto(id, "CODE", tag.name, tag.active, tag.displayOrder, tag.version)
    }

    override suspend fun deleteTag(id: Long) { /* no-op */ }

    // ── Privado ──────────────────────────────────────────────────────────────

    private fun loadFromAssets(): List<MenuItem> {
        val json = context.assets
            .open("menu.json")
            .bufferedReader()
            .use { it.readText() }
        val type = object : TypeToken<List<MenuItem>>() {}.type
        return gson.fromJson(json, type)
    }

    override suspend fun getMenuItemComponents(menuItemId: Long): List<com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse> = emptyList()
}

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

    override fun observeActiveCategories(): Flow<List<String>> =
        flowOf((cachedProducts ?: emptyList()).map { it.categoria }.distinct().sorted())

    // ── Lectura puntual ──────────────────────────────────────────────────────

    override suspend fun getProducts(): List<MenuItem> = withContext(Dispatchers.IO) {
        cachedProducts ?: loadFromAssets().also { cachedProducts = it }
    }

    override suspend fun getCategories(): List<String> =
        getProducts().map { it.categoria }.distinct().sorted()

    // ── Escritura (no-op — JSON es de solo lectura) ──────────────────────────

    override suspend fun saveProduct(item: MenuItem) { /* no-op */ }

    override suspend fun setActive(id: String, activo: Boolean) { /* no-op */ }

    override suspend fun getConfiguration(menuItemId: String): com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto {
        // Mock fake configuration response for Sushi Box Clásica
        if (menuItemId.contains("Sushi Box", ignoreCase = true)) {
            return com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto(
                menuItemId = menuItemId,
                name = "Sushi Box Clásica",
                standaloneOrderable = true,
                basePrice = java.math.BigDecimal("250.00"),
                requiresConfiguration = true,
                groups = listOf(
                    com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto(
                        id = 7,
                        name = "Elige tus rollos",
                        minSelections = 2,
                        maxSelections = 2,
                        allowDuplicates = true,
                        options = listOf(
                            com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto("12", "California", "Rollos Clásicos", java.math.BigDecimal("79.00"), true, false, java.math.BigDecimal.ZERO),
                            com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto("28", "Camarón", "Rollos Camarón", java.math.BigDecimal("99.00"), true, false, java.math.BigDecimal("20.00"))
                        )
                    )
                )
            )
        }
        
        return com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Producto Simple",
            standaloneOrderable = true,
            basePrice = java.math.BigDecimal("79.00"),
            requiresConfiguration = false
        )
    }

    override suspend fun quoteItem(menuItemId: String, request: QuoteRequestDto): QuoteResponseDto {
        // Fake calculation for Sushi Box
        val isSushiBox = request.groups.any { it.groupId == 7 }
        
        if (isSushiBox) {
            var adjustmentTotal = java.math.BigDecimal.ZERO
            val resSelections = request.groups.first { it.groupId == 7 }.selections.map { sel ->
                val adjustment = if (sel.menuItemId == "28") java.math.BigDecimal("20.00") else java.math.BigDecimal.ZERO
                adjustmentTotal += (adjustment * java.math.BigDecimal(sel.quantity))
                
                com.restaurant.sushimei.frontend.data.model.QuoteResponseSelectionDto(
                    menuItemId = sel.menuItemId,
                    name = if (sel.menuItemId == "28") "Camarón" else "California",
                    quantity = sel.quantity,
                    catalogUnitPrice = if (sel.menuItemId == "28") java.math.BigDecimal("99.00") else java.math.BigDecimal("79.00"),
                    priceAdjustment = adjustment
                )
            }
            
            val unitTotal = java.math.BigDecimal("250.00") + adjustmentTotal
            
            return com.restaurant.sushimei.frontend.data.model.QuoteResponseDto(
                menuItemId = menuItemId,
                name = "Sushi Box Clásica",
                quantity = request.quantity,
                baseUnitPrice = java.math.BigDecimal("250.00"),
                baseTotal = java.math.BigDecimal("250.00") * java.math.BigDecimal(request.quantity),
                groups = listOf(
                    com.restaurant.sushimei.frontend.data.model.QuoteResponseGroupDto(7, "Elige tus rollos", resSelections)
                ),
                unitAdjustmentTotal = adjustmentTotal,
                unitTotal = unitTotal,
                total = unitTotal * java.math.BigDecimal(request.quantity)
            )
        }
        
        // Generic fallback
        return com.restaurant.sushimei.frontend.data.model.QuoteResponseDto(
            menuItemId = menuItemId,
            name = "Item",
            quantity = request.quantity,
            baseUnitPrice = java.math.BigDecimal("100.00"),
            baseTotal = java.math.BigDecimal("100.00") * java.math.BigDecimal(request.quantity),
            unitAdjustmentTotal = java.math.BigDecimal.ZERO,
            unitTotal = java.math.BigDecimal("100.00"),
            total = java.math.BigDecimal("100.00") * java.math.BigDecimal(request.quantity)
        )
    }

    override suspend fun getTags(): List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto> {
        return listOf(
            CatalogTagDto("1", "ROLL", "Rollo", true, 1, 1L),
            CatalogTagDto("2", "ROLL_CLASSIC", "Rollo Clásico", true, 2, 1L),
            CatalogTagDto("3", "TOPPING", "Topping", true, 3, 1L)
        )
    }

    override suspend fun createTag(tag: CatalogTagDto): CatalogTagDto {
        return tag.copy(id = "mock-id")
    }

    override suspend fun updateTag(id: String, tag: CatalogTagDto): CatalogTagDto {
        return tag
    }

    override suspend fun deleteTag(id: String) {
        // no-op
    }

    // ── Privado ──────────────────────────────────────────────────────────────

    private fun loadFromAssets(): List<MenuItem> {
        val json = context.assets
            .open("menu.json")
            .bufferedReader()
            .use { it.readText() }
        val type = object : TypeToken<List<MenuItem>>() {}.type
        return gson.fromJson(json, type)
    }
}

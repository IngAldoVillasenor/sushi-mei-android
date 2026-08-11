package com.restaurant.sushimei.frontend.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.restaurant.sushimei.frontend.data.local.MenuDao
import com.restaurant.sushimei.frontend.data.local.MenuItemEntity
import com.restaurant.sushimei.frontend.data.model.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementación de [IMenuRepository] respaldada por Room SQLite.
 *
 * **Seed automático**: la primera vez que se instancia (tabla vacía),
 * carga los 121 productos de `assets/menu.json` en Room. A partir de ahí,
 * todas las ediciones del dueño persisten en SQLite sin tocar el JSON.
 *
 * **Reactividad**: los [Flow] de Room emiten una nueva lista cada vez que
 * la tabla cambia → el POS y la pantalla de Gestión se actualizan en tiempo real.
 *
 * **Nota API-First**: cuando el backend esté listo, crear [ApiMenuRepository]
 * que implemente esta misma interfaz usando Retrofit, y reemplazar la instancia
 * en [provideMenuRepository] sin tocar ningún ViewModel.
 */
class RoomMenuRepository(
    private val dao: MenuDao,
    private val context: Context
) : IMenuRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Seed asíncrono al instanciar — no bloquea el hilo principal
        scope.launch { seedIfNeeded() }
    }

    // ── Lectura reactiva ─────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<MenuItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<MenuItem>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeActiveCategories(): Flow<List<String>> =
        dao.observeActiveCategories()

    override suspend fun refreshCatalog(standaloneOnly: Boolean?) {
        // No-op for local database
    }

    // ── Lectura puntual ──────────────────────────────────────────────────────

    override suspend fun getProducts(): List<MenuItem> =
        withContext(Dispatchers.IO) {
            dao.observeActive().first().map { it.toDomain() }
        }

    override suspend fun getCategories(): List<String> =
        withContext(Dispatchers.IO) {
            dao.observeActiveCategories().first()
        }

    // ── Escritura ────────────────────────────────────────────────────────────

    override suspend fun createProduct(request: com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse {
        return com.restaurant.sushimei.frontend.data.model.MenuItemResponse(
            id = System.currentTimeMillis(),
            name = request.name,
            description = request.description,
            category = request.category,
            price = request.price,
            active = true,
            available = request.available,
            standaloneOrderable = request.standaloneOrderable,
            displayOrder = request.displayOrder,
            tags = emptyList(),
            version = 1L,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )
    }

    override suspend fun updateProduct(id: Long, request: com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse {
        return com.restaurant.sushimei.frontend.data.model.MenuItemResponse(
            id = id,
            name = request.name,
            description = request.description,
            category = request.category,
            price = request.price,
            active = request.active,
            available = request.available,
            standaloneOrderable = request.standaloneOrderable,
            displayOrder = request.displayOrder,
            tags = emptyList(),
            version = request.version,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )
    }

    override suspend fun deleteProduct(id: Long) {
        // mock
    }

    override suspend fun setActive(id: Long, activo: Boolean) =
        withContext(Dispatchers.IO) {
            dao.setActive(id.toString(), activo)
        }

    override suspend fun getConfiguration(menuItemId: Long): com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto {
        // En un caso real, esto llamaría a Retrofit. Por ahora, mock genérico:
        return com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Producto de Room",
            standaloneOrderable = true,
            basePrice = java.math.BigDecimal.ZERO,
            requiresConfiguration = false
        )
    }

    override suspend fun quoteItem(menuItemId: Long, request: com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto): com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto {
        // En un caso real, esto llamaría a Retrofit. Por ahora, mock genérico:
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
        // En un caso real, esto llamaría a Retrofit. Por ahora, mock genérico:
        return emptyList()
    }

    override suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.TagCreateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto {
        return com.restaurant.sushimei.frontend.data.model.CatalogTagDto(id = 1L, code = tag.code, name = tag.name, active = true, displayOrder = tag.displayOrder, version = 1L)
    }

    override suspend fun updateTag(id: Long, tag: com.restaurant.sushimei.frontend.data.model.TagUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto {
        return com.restaurant.sushimei.frontend.data.model.CatalogTagDto(id = id, code = "CODE", name = tag.name, active = tag.active, displayOrder = tag.displayOrder, version = tag.version + 1)
    }

    override suspend fun deleteTag(id: Long) {
        // No-op
    }

    // ── Seed ─────────────────────────────────────────────────────────────────

    /**
     * Si la tabla `menu_items` está vacía, carga todos los productos desde
     * `assets/menu.json`. Se ejecuta una sola vez en la vida del dispositivo.
     * La estrategia IGNORE en el DAO protege contra dobles ejecuciones.
     */
    private suspend fun seedIfNeeded() {
        if (dao.count() > 0) return
        val products = loadFromAssets()
        dao.insertAll(products.map { it.toEntity() })
    }

    private fun loadFromAssets(): List<MenuItem> {
        val json = context.assets
            .open("menu.json")
            .bufferedReader()
            .use { it.readText() }
        val type = object : TypeToken<List<MenuItem>>() {}.type
        return Gson().fromJson(json, type)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mappers: MenuItem ↔ MenuItemEntity
// ─────────────────────────────────────────────────────────────────────────────

private fun MenuItemEntity.toDomain() = MenuItem(
    id          = id,
    nombre      = nombre,
    categoria   = categoria,
    precio      = precio,
    descripcion = descripcion,
    emoji       = emoji
)

private fun MenuItem.toEntity(activo: Boolean = true) = MenuItemEntity(
    id          = id,
    nombre      = nombre,
    categoria   = categoria,
    precio      = precio,
    descripcion = descripcion,
    emoji       = emoji,
    activo      = activo
)

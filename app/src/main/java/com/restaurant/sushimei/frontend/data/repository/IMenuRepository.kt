package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.MenuItem
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de menú.
 *
 * Implementación actual:
 *   - [RoomMenuRepository]: Room SQLite, seeded desde assets/menu.json
 *
 * Implementación futura:
 *   - ApiMenuRepository: Retrofit → endpoints del backend Spring Boot
 *
 * Los métodos que devuelven [Flow] son reactivos: cualquier cambio en la
 * fuente de datos (Room o API) se propaga automáticamente a la UI.
 */
interface IMenuRepository {

    // ── Lectura reactiva (Flow) ──────────────────────────────────────────────

    /**
     * Flow de todos los productos (activos e inactivos).
     * Usado por [MenuManagementScreen] para el catálogo completo del dueño.
     */
    fun observeAll(): Flow<List<MenuItem>>

    /**
     * Flow de productos activos únicamente.
     * Usado por [PosScreen] — los productos inactivos no pueden venderse.
     */
    fun observeActive(): Flow<List<MenuItem>>

    /**
     * Flow de categorías únicas de productos activos, ordenadas alfabéticamente.
     */
    fun observeActiveCategories(): Flow<List<String>>

    // ── Lectura puntual (suspend) ─────────────────────────────────────────────

    /** Snapshot de productos activos. Compatible con tests unitarios existentes. */
    suspend fun getProducts(): List<MenuItem>

    /** Snapshot de categorías únicas ordenadas. */
    suspend fun getCategories(): List<String>

    // ── Escritura (CRUD) ──────────────────────────────────────────────────────

    /**
     * Crea o actualiza un producto.
     * Si [MenuItem.id] ya existe en la BD, se sobreescribe.
     * Si es nuevo, se inserta.
     *
     * Nota API-First: en producción, el precio canónico vendrá del backend.
     * Los cambios locales solo aplican mientras el backend no tenga el endpoint.
     */
    suspend fun saveProduct(item: MenuItem)

    /**
     * Activa o desactiva un producto.
     * Un producto inactivo ([activo] = false) desaparece del POS en tiempo real.
     */
    suspend fun setActive(id: String, activo: Boolean)

    // =========================================================================
    // PHASE 6A2: Backend API Contract
    // =========================================================================

    /**
     * Obtiene la configuración (grupos y opciones) para un producto.
     */
    suspend fun getConfiguration(menuItemId: String): com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto

    /**
     * Solicita una cotización autoritativa al backend para una configuración.
     */
    suspend fun quoteItem(menuItemId: String, request: com.restaurant.sushimei.frontend.data.model.QuoteRequestDto): com.restaurant.sushimei.frontend.data.model.QuoteResponseDto

    /**
     * Obtiene todos los Tags del catálogo (ej. ROLL_CLASSIC).
     */
    suspend fun getTags(): List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>

    suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.CatalogTagDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto
    suspend fun updateTag(id: String, tag: com.restaurant.sushimei.frontend.data.model.CatalogTagDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto
    suspend fun deleteTag(id: String)
}


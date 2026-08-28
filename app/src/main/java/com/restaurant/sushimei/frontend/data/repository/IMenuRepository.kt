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
     * Flow de categorías (Strings) que tienen al menos un producto activo.
     */
    fun observeActiveCategories(): Flow<List<String>>

    /**
     * Fuerza la actualización del catálogo desde la fuente remota.
     */
    suspend fun refreshCatalog(standaloneOnly: Boolean? = null)

    // ── Lectura puntual (suspend) ─────────────────────────────────────────────

    /** Snapshot de productos activos. Compatible con tests unitarios existentes. */
    suspend fun getProducts(): List<MenuItem>

    /** Snapshot de categorías únicas ordenadas. */
    suspend fun getCategories(): List<String>

    // ── Escritura (CRUD) ──────────────────────────────────────────────────────

    /**
     * Crea un nuevo producto en el catálogo.
     */
    suspend fun createProduct(request: com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse

    /**
     * Actualiza un producto existente.
     */
    suspend fun updateProduct(id: Long, request: com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.MenuItemResponse

    /**
     * Elimina un producto.
     */
    suspend fun deleteProduct(id: Long)

    /**
     * Activa o desactiva un producto.
     * Un producto inactivo ([activo] = false) desaparece del POS en tiempo real.
     */
    suspend fun setActive(id: Long, activo: Boolean)

    // =========================================================================
    // PHASE 6A2: Backend API Contract
    // =========================================================================

    /**
     * Obtiene la configuración (grupos y opciones) para un producto.
     */
    suspend fun getConfiguration(menuItemId: Long): com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto

    /**
     * Solicita una cotización autoritativa al backend para una configuración.
     */
    suspend fun quoteItem(menuItemId: Long, request: com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto): com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto

    /**
     * Obtiene todos los Tags del catálogo (ej. ROLL_CLASSIC).
     */
    suspend fun getTags(): List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>

    suspend fun createTag(tag: com.restaurant.sushimei.frontend.data.model.TagCreateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto
    suspend fun updateTag(id: Long, tag: com.restaurant.sushimei.frontend.data.model.TagUpdateRequestDto): com.restaurant.sushimei.frontend.data.model.CatalogTagDto
    suspend fun deleteTag(id: Long)

    /** Fetches generic default components for customization. */
    suspend fun getMenuItemComponents(menuItemId: Long): List<com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse>

    suspend fun getMenuItemConfigurationDefinitionResponse(id: Long): com.restaurant.sushimei.frontend.data.model.MenuItemConfigurationDefinitionResponse
    suspend fun createSelectionGroup(itemId: Long, request: com.restaurant.sushimei.frontend.data.model.CreateMenuSelectionGroupRequest): com.restaurant.sushimei.frontend.data.model.MenuSelectionGroupResponse
    suspend fun updateSelectionGroup(itemId: Long, groupId: Long, request: com.restaurant.sushimei.frontend.data.model.UpdateMenuSelectionGroupRequest): com.restaurant.sushimei.frontend.data.model.MenuSelectionGroupResponse
    suspend fun deleteSelectionGroup(itemId: Long, groupId: Long)
    suspend fun createSelectionRule(groupId: Long, request: com.restaurant.sushimei.frontend.data.model.CreateMenuSelectionRuleRequest): com.restaurant.sushimei.frontend.data.model.MenuSelectionRuleResponse
    suspend fun updateSelectionRule(groupId: Long, ruleId: Long, request: com.restaurant.sushimei.frontend.data.model.UpdateMenuSelectionRuleRequest): com.restaurant.sushimei.frontend.data.model.MenuSelectionRuleResponse
    suspend fun deleteSelectionRule(groupId: Long, ruleId: Long)

}
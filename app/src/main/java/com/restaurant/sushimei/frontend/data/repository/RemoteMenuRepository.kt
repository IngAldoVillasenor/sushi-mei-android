package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.CatalogItemDto
import com.restaurant.sushimei.frontend.data.model.CatalogTagDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.QuoteRequestDto
import com.restaurant.sushimei.frontend.data.model.QuoteResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class RemoteMenuRepository(
    private val api: SushiMeiApi
) : IMenuRepository {

    private val allProductsFlow = MutableStateFlow<List<MenuItem>>(emptyList())
    
    // Convert DTO to Domain
    private fun CatalogItemDto.toDomain() = MenuItem(
        id = id,
        nombre = name,
        categoria = category,
        precio = price,
        descripcion = description ?: "",
        emoji = "🍣", // Fallback emoji for remote items without specific emoji field
        activo = active,
        standaloneOrderable = standaloneOrderable,
        tags = tags ?: emptyList()
    )

    override fun observeAll(): Flow<List<MenuItem>> = allProductsFlow.asStateFlow()

    override fun observeActive(): Flow<List<MenuItem>> = allProductsFlow.map { list ->
        list.filter { it.activo }
    }

    override fun observeActiveCategories(): Flow<List<String>> = observeActive().map { list ->
        list.map { it.categoria }.distinct().sorted()
    }

    override suspend fun getProducts(): List<MenuItem> {
        val response = api.getMenuItems()
        if (response.isSuccessful) {
            val items = response.body()?.map { it.toDomain() } ?: emptyList()
            allProductsFlow.value = items
            return items
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getCategories(): List<String> {
        return getProducts().map { it.categoria }.distinct().sorted()
    }

    override suspend fun saveProduct(item: MenuItem) {
        // En Phase 6A2 no se ha proporcionado el DTO para crear o editar MenuItems 
        // a través de POST /api/v1/menu/items, así que esto permanece como no-op
        // o podría implementarse cuando el endpoint esté documentado.
        throw UnsupportedOperationException("Guardar productos mediante la app está deshabilitado en favor del backend ERP.")
    }

    override suspend fun setActive(id: String, activo: Boolean) {
        throw UnsupportedOperationException("Activar/Desactivar requiere un endpoint ERP no provisto.")
    }

    override suspend fun getConfiguration(menuItemId: String): ConfigurationResponseDto {
        val response = api.getMenuItemConfiguration(menuItemId)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Configuration body null")
        } else if (response.code() == 404) {
            throw Exception("MENU_ITEM_NOT_FOUND")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun quoteItem(menuItemId: String, request: QuoteRequestDto): QuoteResponseDto {
        // Enviar la petición al backend para la cotización recursiva
        val response = api.quoteMenuItem(menuItemId, request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Quote response body null")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun getTags(): List<CatalogTagDto> {
        val response = api.getAllTags()
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun createTag(tag: CatalogTagDto): CatalogTagDto {
        val response = api.createTag(tag)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Create tag body null")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun updateTag(id: String, tag: CatalogTagDto): CatalogTagDto {
        val response = api.updateTag(id, tag)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Update tag body null")
        } else if (response.code() == 409) {
            throw Exception("VERSION_CONFLICT")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun deleteTag(id: String) {
        val response = api.deleteTag(id)
        if (!response.isSuccessful) {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }
}

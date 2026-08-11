package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class RemoteMenuRepository(
    private val api: SushiMeiApi
) : IMenuRepository {

    private val allProductsFlow = MutableStateFlow<List<MenuItem>>(emptyList())
    
    // Convert DTO to Domain
    private fun MenuItemResponse.toDomain() = MenuItem(
        id = id,
        nombre = name,
        categoria = category,
        precio = price,
        descripcion = description ?: "",
        emoji = "🍣", // Fallback emoji for remote items without specific emoji field
        activo = active,
        standaloneOrderable = standaloneOrderable,
        tags = tags
    )

    override fun observeAll(): Flow<List<MenuItem>> = allProductsFlow.asStateFlow()

    override fun observeActive(): Flow<List<MenuItem>> = allProductsFlow.map { list ->
        list.filter { it.activo }
    }

    override fun observeActiveCategories(): Flow<List<String>> = observeActive().map { list ->
        list.map { it.categoria }.distinct().sorted()
    }

    override suspend fun refreshCatalog(standaloneOnly: Boolean?) {
        val response = api.getMenuItems(standaloneOnly = standaloneOnly)
        if (response.isSuccessful) {
            val items = response.body()?.map { it.toDomain() } ?: emptyList()
            allProductsFlow.value = items
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun getProducts(): List<MenuItem> {
        refreshCatalog(standaloneOnly = null)
        return allProductsFlow.value
    }

    override suspend fun getCategories(): List<String> {
        return getProducts().map { it.categoria }.distinct().sorted()
    }

    override suspend fun createProduct(request: MenuItemCreateRequestDto): MenuItemResponse {
        val response = api.createMenuItem(request)
        if (response.isSuccessful) {
            getProducts() // Refresh catalog
            return response.body() ?: throw Exception("Create product body null")
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun updateProduct(id: Long, request: MenuItemUpdateRequestDto): MenuItemResponse {
        val response = api.updateMenuItem(id, request)
        if (response.isSuccessful) {
            getProducts() // Refresh catalog
            return response.body() ?: throw Exception("Update product body null")
        } else if (response.code() == 409) {
            throw Exception("VERSION_CONFLICT")
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun deleteProduct(id: Long) {
        val response = api.deleteMenuItem(id)
        if (response.isSuccessful) {
            getProducts() // Refresh catalog
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    override suspend fun setActive(id: Long, activo: Boolean) {
        // Obtenemos el producto actual para actualizar su estado (esto requeriría saber su version actual)
        // Por ahora, dejamos esto como una operación no soportada directamente a menos que pasemos la version.
        throw UnsupportedOperationException("Usar updateProduct enviando todo el MenuItemUpdateRequestDto.")
    }

    override suspend fun getConfiguration(menuItemId: Long): ConfigurationResponseDto {
        val response = api.getMenuItemConfiguration(menuItemId)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Configuration body null")
        } else if (response.code() == 404) {
            throw Exception("MENU_ITEM_NOT_FOUND")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun quoteItem(menuItemId: Long, request: ItemQuoteRequestDto): ItemQuoteResponseDto {
        val response = api.quoteMenuItem(menuItemId, request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Quote response body null")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun getTags(): List<CatalogTagDto> {
        val response = api.getTags(includeInactive = true)
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun createTag(tag: TagCreateRequestDto): CatalogTagDto {
        val response = api.createTag(tag)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Create tag body null")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun updateTag(id: Long, tag: TagUpdateRequestDto): CatalogTagDto {
        val response = api.updateTag(id, tag)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Update tag body null")
        } else if (response.code() == 409) {
            throw Exception("VERSION_CONFLICT")
        } else {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }

    override suspend fun deleteTag(id: Long) {
        val response = api.deleteTag(id)
        if (!response.isSuccessful) {
            throw Exception("Error ${response.code()}: ${response.errorBody()?.string()}")
        }
    }
}

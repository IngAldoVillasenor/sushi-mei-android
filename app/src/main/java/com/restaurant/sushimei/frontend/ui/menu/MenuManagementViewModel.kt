package com.restaurant.sushimei.frontend.ui.menu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.local.provideMenuRepository
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.toDomain
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel para la pantalla de Gestión de Menú.
 *
 * Expone:
 *  - [filteredProducts]: lista filtrada por búsqueda y categoría (reactiva)
 *  - [categories]: categorías únicas disponibles
 *  - [selectedProduct]: producto cargado en el formulario del panel derecho
 *  - [isSaving]: indicador de operación en curso
 */
sealed interface MenuManagementUiState {
    data class Success(
        val searchQuery: String = "",
        val selectedCategory: String? = null,
        val filteredProducts: List<MenuItem> = emptyList(),
        val categories: List<String> = emptyList(),
        val selectedProduct: MenuItem? = null,
        val isSaving: Boolean = false,
        val saveSuccess: Boolean = false,
        val saveError: String? = null
    ) : MenuManagementUiState
}

class MenuManagementViewModel(
    private val repository: IMenuRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                repository.refreshCatalog(includeInactive = true)
            } catch (e: Exception) {
                // Initial load best-effort
            }
        }
    }


    // ── Filtros ──────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // ── Datos ────────────────────────────────────────────────────────────────

    /** Todos los productos (activos e inactivos) filtrados por búsqueda y categoría. */
    val filteredProducts: StateFlow<List<MenuItem>> = combine(
        repository.observeAll(),
        _searchQuery,
        _selectedCategory
    ) { products, query, category ->
        products
            .filter { category == null || it.categoria == category }
            .filter { query.isBlank() || it.nombre.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Categorías únicas de todos los productos (activos + inactivos). */
    val categories: StateFlow<List<String>> = combine(
        repository.observeAll()
    ) { productsArray ->
        productsArray[0].map { it.categoria }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Producto seleccionado (panel de formulario) ──────────────────────────

    private val _selectedProduct = MutableStateFlow<MenuItem?>(null)


    /** true mientras se guarda un producto. */
    private val _isSaving = MutableStateFlow(false)

    /** true después de guardar con éxito (para mostrar feedback visual). */
    private val _saveSuccess = MutableStateFlow(false)
    private val _saveError = MutableStateFlow<String?>(null)

    private val filterState = combine(
        _searchQuery,
        _selectedCategory,
        filteredProducts,
        categories
    ) { search, category, filtered, cats ->
        MenuManagementUiState.Success(
            searchQuery = search,
            selectedCategory = category,
            filteredProducts = filtered,
            categories = cats
        )
    }

    val uiState: StateFlow<MenuManagementUiState> = combine(
        filterState,
        _selectedProduct,
        _isSaving,
        _saveSuccess,
        _saveError
    ) { filter, selected, saving, success, error ->
        filter.copy(
            selectedProduct = selected,
            isSaving = saving,
            saveSuccess = success,
            saveError = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MenuManagementUiState.Success())

    // ── Acciones ─────────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun onCategorySelected(category: String?) { _selectedCategory.value = category }

    /** Carga un producto existente en el formulario. */
    fun selectProduct(item: MenuItem) { _selectedProduct.value = item }

    /** Abre el formulario en blanco para crear un producto nuevo. */
    fun newProduct() {
        _selectedProduct.value = MenuItem(
            id          = 0L,
            nombre      = "",
            categoria   = "",
            precio      = java.math.BigDecimal.ZERO,
            descripcion = "",
            emoji       = "🍣"
        )
    }

    /** Descarta los cambios del formulario. */
    fun clearSelection() {
        _selectedProduct.value = null
        _saveError.value = null
    }

    /**
     * Guarda el producto (create o update) en Room.
     * Los cambios se propagan inmediatamente al POS a través del Flow reactivo.
     */
    fun saveProduct(item: MenuItem) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveSuccess.value = false
            _saveError.value = null
            try {
                if (item.id == 0L) {
                    val req = com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto(
                        name = item.nombre,
                        description = item.descripcion,
                        category = item.categoria,
                        price = item.precio,
                        available = item.available,
                        standaloneOrderable = item.standaloneOrderable,
                        displayOrder = item.displayOrder
                    )
                    val response = repository.createProduct(req)

                    _selectedProduct.value = response.toDomain()
                } else {
                    val req = com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto(
                        name = item.nombre,
                        description = item.descripcion,
                        category = item.categoria,
                        price = item.precio,
                        active = item.activo,
                        available = item.available,
                        standaloneOrderable = item.standaloneOrderable,
                        displayOrder = item.displayOrder,
                        version = item.version
                    )
                    val response = repository.updateProduct(item.id, req)

                    _selectedProduct.value = item.copy(
                        nombre = response.name,
                        descripcion = response.description ?: "",
                        categoria = response.category,
                        precio = response.price,
                        activo = response.active,
                        available = response.available,
                        standaloneOrderable = response.standaloneOrderable,
                        displayOrder = response.displayOrder,
                        version = response.version
                    )
                }
                _saveSuccess.value = true
            } catch (e: com.restaurant.sushimei.frontend.data.api.VersionConflictException) {
                try {
                    repository.refreshCatalog(includeInactive = true)
                    val latestList = repository.observeAll().first()
                    val latest = latestList.find { it.id == item.id }
                    if (latest != null) {
                        _selectedProduct.value = latest
                    }
                    _saveError.value = "El producto fue modificado por otro usuario. Se han recargado los datos más recientes. Por favor revisa y aplica tus cambios nuevamente.${e.referenceSuffix()}"
                } catch (refreshEx: Exception) {
                    _saveError.value = "El producto fue modificado por otro usuario, pero no se pudieron obtener los datos actualizados. Intenta de nuevo.${e.referenceSuffix()}"
                }
            } catch (e: Exception) {
                _saveError.value = "Error al guardar: ${e.message ?: "Desconocido"}"
            } finally {
                _isSaving.value = false
            }
        }
    }
fun toggleActive(item: MenuItem, activo: Boolean) {
        viewModelScope.launch {
            _saveError.value = null
            try {
                val req = com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto(
                    name = item.nombre,
                    description = item.descripcion,
                    category = item.categoria,
                    price = item.precio,
                    active = activo,
                    available = item.available,
                    standaloneOrderable = item.standaloneOrderable,
                    displayOrder = item.displayOrder,
                    version = item.version
                )
                val response = repository.updateProduct(item.id, req)
                val updated = response.toDomain()
                if (_selectedProduct.value?.id == item.id) {
                    _selectedProduct.value = updated
                }
            } catch (e: com.restaurant.sushimei.frontend.data.api.VersionConflictException) {
                try {
                    repository.refreshCatalog(includeInactive = true)
                    val latestList = repository.observeAll().first()
                    val latest = latestList.find { it.id == item.id }
                    if (latest != null && _selectedProduct.value?.id == item.id) {
                        _selectedProduct.value = latest
                    }
                    _saveError.value = "No se pudo cambiar el estado. El producto fue modificado por otro usuario. Se ha recargado el catálogo.${e.referenceSuffix()}"
                } catch (refreshEx: Exception) {
                    _saveError.value = "No se pudo cambiar el estado (conflicto) y falló la actualización del catálogo.${e.referenceSuffix()}"
                }
            } catch (e: Exception) {
                _saveError.value = "Error al cambiar estado: ${e.message ?: "Desconocido"}"
            }
        }
    }
fun acknowledgeSaveSuccess() { _saveSuccess.value = false }

    fun acknowledgeSaveError() { _saveError.value = null }

    // ── Factory ──────────────────────────────────────────────────────────────

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MenuManagementViewModel(provideMenuRepository(context)) as T
            }
    }
}

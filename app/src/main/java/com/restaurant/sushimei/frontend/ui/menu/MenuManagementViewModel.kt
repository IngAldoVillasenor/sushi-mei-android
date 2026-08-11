package com.restaurant.sushimei.frontend.ui.menu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.local.provideMenuRepository
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        val saveSuccess: Boolean = false
    ) : MenuManagementUiState
}

class MenuManagementViewModel(
    private val repository: IMenuRepository
) : ViewModel() {

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
        _saveSuccess
    ) { filter, selected, saving, success ->
        filter.copy(
            selectedProduct = selected,
            isSaving = saving,
            saveSuccess = success
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
    fun clearSelection() { _selectedProduct.value = null }

    /**
     * Guarda el producto (create o update) en Room.
     * Los cambios se propagan inmediatamente al POS a través del Flow reactivo.
     */
    fun saveProduct(item: MenuItem) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveSuccess.value = false
            try {
                if (item.id == 0L) {
                    val req = com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto(
                        name = item.nombre,
                        description = item.descripcion,
                        category = item.categoria,
                        price = item.precio,
                        available = item.activo,
                        standaloneOrderable = true,
                        displayOrder = 0
                    )
                    repository.createProduct(req)
                } else {
                    val req = com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto(
                        name = item.nombre,
                        description = item.descripcion,
                        category = item.categoria,
                        price = item.precio,
                        active = item.activo,
                        available = item.activo,
                        standaloneOrderable = true,
                        displayOrder = 0,
                        version = 1L
                    )
                    repository.updateProduct(item.id, req)
                }
                
                _saveSuccess.value = true
                _selectedProduct.value = item // actualiza el form con datos guardados
            } catch (e: Exception) {
                // handle error
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Activa o desactiva un producto.
     * Si se desactiva, desaparece del POS en tiempo real sin reiniciar la app.
     */
    fun toggleActive(item: MenuItem, activo: Boolean) {
        viewModelScope.launch {
            repository.setActive(item.id, activo)
            // Si el producto estaba en el formulario, refleja el nuevo estado
            if (_selectedProduct.value?.id == item.id) {
                _selectedProduct.value = item.copy() // trigger recomposition
            }
        }
    }

    fun acknowledgeSaveSuccess() { _saveSuccess.value = false }

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

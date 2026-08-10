package com.restaurant.sushimei.frontend.ui.admin.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.CatalogTagDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminTagsUiState(
    val isLoading: Boolean = false,
    val tags: List<CatalogTagDto> = emptyList(),
    val errorMessage: String? = null
)

class AdminTagsViewModel(
    private val repository: IMenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminTagsUiState())
    val uiState: StateFlow<AdminTagsUiState> = _uiState.asStateFlow()

    init {
        loadTags()
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val fetchedTags = repository.getTags()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tags = fetchedTags
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar tags: ${e.message}"
                )
            }
        }
    }

    fun saveTag(tag: CatalogTagDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // If tag has empty ID or pseudo ID, it's create, else update
                val isNew = tag.id.isEmpty()
                val savedTag = if (isNew) {
                    repository.createTag(tag)
                } else {
                    repository.updateTag(tag.id, tag)
                }
                
                val current = _uiState.value.tags.toMutableList()
                val idx = current.indexOfFirst { it.id == savedTag.id }
                if (idx >= 0) {
                    current[idx] = savedTag
                } else {
                    current.add(savedTag)
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tags = current
                )
            } catch (e: Exception) {
                val isConflict = e.message?.contains("VERSION_CONFLICT") == true
                val msg = if (isConflict) {
                    "Este elemento fue modificado desde otro dispositivo."
                } else {
                    "Error al guardar tag: ${e.message}"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
            }
        }
    }
}

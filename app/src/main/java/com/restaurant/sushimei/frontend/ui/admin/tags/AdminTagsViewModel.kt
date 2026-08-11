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
                // If tag has id 0L, it's create, else update
                val isNew = tag.id == 0L
                val savedTag = if (isNew) {
                    val createReq = com.restaurant.sushimei.frontend.data.model.TagCreateRequestDto(
                        code = tag.code,
                        name = tag.name,
                        displayOrder = tag.displayOrder
                    )
                    repository.createTag(createReq)
                } else {
                    val updateReq = com.restaurant.sushimei.frontend.data.model.TagUpdateRequestDto(
                        name = tag.name,
                        active = tag.active,
                        displayOrder = tag.displayOrder,
                        version = tag.version
                    )
                    repository.updateTag(tag.id, updateReq)
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

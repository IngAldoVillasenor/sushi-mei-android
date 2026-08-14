package com.restaurant.sushimei.frontend.ui.admin.promotions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.Promotion
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PromotionsUiState(
    val isLoading: Boolean = false,
    val promotions: List<Promotion> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class PromotionsViewModel(
    private val repository: IPromotionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromotionsUiState())
    val uiState: StateFlow<PromotionsUiState> = _uiState.asStateFlow()

    init {
        loadPromotions()
    }

    fun loadPromotions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val promotions = repository.getPromotions()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    promotions = promotions,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar promociones: ${e.message}"
                )
            }
        }
    }

    fun savePromotion(promotion: Promotion) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, saveSuccess = false)
            try {
                val saved = if (promotion.id > 0L) {
                    repository.updatePromotion(promotion)
                } else {
                    repository.createPromotion(promotion)
                }

                val current = _uiState.value.promotions
                val updated = if (current.any { it.id == saved.id }) {
                    current.map { existing -> if (existing.id == saved.id) saved else existing }
                } else {
                    current + saved
                }.sortedWith(compareByDescending<Promotion> { it.priority }.thenBy { it.id })

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    promotions = updated,
                    errorMessage = null,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Error desconocido al guardar la promoción"
                )
            }
        }
    }

    fun acknowledgeSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

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
                // In a real flow, we could observe `repository.observePromotions()` instead of single fetch.
                repository.observePromotions().collect { list ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        promotions = list
                    )
                }
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
                // If it's a new promotion, we rely on checking if the ID was just locally generated or really exists
                // In real app, might separate create vs update
                if (uiState.value.promotions.any { it.id == promotion.id }) {
                    repository.updatePromotion(promotion)
                } else {
                    repository.createPromotion(promotion)
                }
                
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                // Aquí simulamos atrapar el HTTP 409 Conflict o similar
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

package com.restaurant.sushimei.frontend.ui.pos.configurator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.model.QuoteRequestDto
import com.restaurant.sushimei.frontend.data.model.QuoteRequestGroupDto
import com.restaurant.sushimei.frontend.data.model.QuoteRequestSelectionDto
import com.restaurant.sushimei.frontend.data.model.QuoteResponseDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class QuoteState {
    NOT_REQUESTED,
    LOADING,
    VALID,
    INVALID, // Configuración local inválida (ej. minSelections no cumplido)
    ERROR
}

data class ConfiguratorUiState(
    val isLoadingConfig: Boolean = false,
    val configuration: ConfigurationResponseDto? = null,
    val selections: Map<Int, List<ConfigurationOptionDto>> = emptyMap(), // GroupId -> List of Selections (allows duplicates if configured)
    val quoteState: QuoteState = QuoteState.NOT_REQUESTED,
    val latestQuote: QuoteResponseDto? = null,
    val errorMessage: String? = null
)

class ConfiguratorViewModel(
    private val menuRepository: IMenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfiguratorUiState())
    val uiState: StateFlow<ConfiguratorUiState> = _uiState.asStateFlow()

    private var quoteJob: Job? = null

    fun loadConfiguration(menuItemId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingConfig = true)
            try {
                val config = menuRepository.getConfiguration(menuItemId)
                _uiState.value = _uiState.value.copy(
                    isLoadingConfig = false,
                    configuration = config,
                    selections = emptyMap(),
                    quoteState = QuoteState.NOT_REQUESTED
                )
                validateAndQuote()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingConfig = false,
                    errorMessage = "Error al cargar la configuración: ${e.message}"
                )
            }
        }
    }

    fun addSelection(groupId: Int, option: ConfigurationOptionDto) {
        val currentSelections = _uiState.value.selections.toMutableMap()
        val groupSelections = currentSelections[groupId]?.toMutableList() ?: mutableListOf()
        
        val groupConfig = _uiState.value.configuration?.groups?.find { it.id == groupId } ?: return
        
        if (groupSelections.size >= groupConfig.maxSelections) return // Max reached
        
        if (!groupConfig.allowDuplicates && groupSelections.any { it.menuItemId == option.menuItemId }) {
            return // Duplicates not allowed
        }
        
        groupSelections.add(option)
        currentSelections[groupId] = groupSelections
        
        _uiState.value = _uiState.value.copy(selections = currentSelections)
        validateAndQuote()
    }

    fun removeSelection(groupId: Int, option: ConfigurationOptionDto) {
        val currentSelections = _uiState.value.selections.toMutableMap()
        val groupSelections = currentSelections[groupId]?.toMutableList() ?: return
        
        groupSelections.remove(option)
        currentSelections[groupId] = groupSelections
        
        _uiState.value = _uiState.value.copy(selections = currentSelections)
        validateAndQuote()
    }

    private fun validateAndQuote() {
        val state = _uiState.value
        val config = state.configuration ?: return
        
        var isValid = true
        for (group in config.groups) {
            val count = state.selections[group.id]?.size ?: 0
            if (count < group.minSelections || count > group.maxSelections) {
                isValid = false
                break
            }
        }
        
        if (!isValid) {
            _uiState.value = state.copy(quoteState = QuoteState.INVALID, latestQuote = null)
            return
        }
        
        // Debounce quoting
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(quoteState = QuoteState.LOADING)
            delay(300) // 300ms debounce
            
            try {
                val requestGroups = state.selections.map { (groupId, options) ->
                    val groupedOptions = options.groupBy { it.menuItemId }
                    QuoteRequestGroupDto(
                        groupId = groupId,
                        selections = groupedOptions.map { (itemId, list) ->
                            QuoteRequestSelectionDto(
                                menuItemId = itemId,
                                quantity = list.size,
                                groups = emptyList() // Nested configs not fully implemented in state yet
                            )
                        }
                    )
                }
                
                val request = QuoteRequestDto(
                    quantity = 1,
                    groups = requestGroups
                )
                
                val quote = menuRepository.quoteItem(request)
                _uiState.value = _uiState.value.copy(
                    quoteState = QuoteState.VALID,
                    latestQuote = quote
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    quoteState = QuoteState.ERROR,
                    errorMessage = "Error en la cotización: ${e.message}"
                )
            }
        }
    }
}

package com.restaurant.sushimei.frontend.ui.admin.configurator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ConfigurationBuilderUiState(
    val isLoading: Boolean = false,
    val configuration: ConfigurationResponseDto? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

class ConfigurationBuilderViewModel(
    private val repository: IMenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigurationBuilderUiState())
    val uiState: StateFlow<ConfigurationBuilderUiState> = _uiState.asStateFlow()

    private var currentMenuItemId: String = ""

    fun loadConfiguration(menuItemId: String) {
        currentMenuItemId = menuItemId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, isSaved = false)
            try {
                // We try to load existing config.
                // In Fake repository, it might fail or return a default if it doesn't exist.
                val config = repository.getConfiguration(menuItemId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    configuration = config
                )
            } catch (e: Exception) {
                // If it fails, we initialize an empty configuration builder state
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    configuration = ConfigurationResponseDto(
                        menuItemId = menuItemId,
                        name = "Nueva Configuración",
                        standaloneOrderable = true,
                        basePrice = 0.0,
                        requiresConfiguration = true,
                        groups = emptyList()
                    )
                )
            }
        }
    }

    fun addGroup(name: String, minSelections: Int, maxSelections: Int, allowDuplicates: Boolean) {
        val currentConfig = _uiState.value.configuration ?: return
        val newGroup = ConfigurationGroupDto(
            id = (currentConfig.groups.maxOfOrNull { it.id } ?: 0) + 1,
            name = name,
            minSelections = minSelections,
            maxSelections = maxSelections,
            allowDuplicates = allowDuplicates,
            options = emptyList()
        )
        val updatedGroups = currentConfig.groups + newGroup
        _uiState.value = _uiState.value.copy(
            configuration = currentConfig.copy(groups = updatedGroups)
        )
    }

    fun removeGroup(groupId: Int) {
        val currentConfig = _uiState.value.configuration ?: return
        val updatedGroups = currentConfig.groups.filterNot { it.id == groupId }
        _uiState.value = _uiState.value.copy(
            configuration = currentConfig.copy(groups = updatedGroups)
        )
    }

    fun addOption(groupId: Int, name: String, targetMenuItemId: String?, priceAdjustment: Double) {
        val currentConfig = _uiState.value.configuration ?: return
        val updatedGroups = currentConfig.groups.map { group ->
            if (group.id == groupId) {
                val newOption = ConfigurationOptionDto(
                    menuItemId = targetMenuItemId ?: UUID.randomUUID().toString(),
                    name = name,
                    category = "N/A",
                    catalogPrice = 0.0, 
                    available = true,
                    requiresConfiguration = false,
                    priceAdjustment = priceAdjustment
                )
                group.copy(options = group.options + newOption)
            } else {
                group
            }
        }
        _uiState.value = _uiState.value.copy(
            configuration = currentConfig.copy(groups = updatedGroups)
        )
    }

    fun removeOption(groupId: Int, optionMenuItemId: String) {
        val currentConfig = _uiState.value.configuration ?: return
        val updatedGroups = currentConfig.groups.map { group ->
            if (group.id == groupId) {
                group.copy(options = group.options.filterNot { it.menuItemId == optionMenuItemId })
            } else {
                group
            }
        }
        _uiState.value = _uiState.value.copy(
            configuration = currentConfig.copy(groups = updatedGroups)
        )
    }

    fun updateBaseProperties(name: String, basePrice: Double, standaloneOrderable: Boolean, requiresConfiguration: Boolean) {
        val currentConfig = _uiState.value.configuration ?: return
        _uiState.value = _uiState.value.copy(
            configuration = currentConfig.copy(
                name = name,
                basePrice = basePrice,
                standaloneOrderable = standaloneOrderable,
                requiresConfiguration = requiresConfiguration
            )
        )
    }

    fun saveConfiguration() {
        // In a real app this would send the updated configuration to the backend
        // e.g., repository.saveConfiguration(menuItemId, _uiState.value.configuration!!)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            kotlinx.coroutines.delay(500) // Simulate network
            _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
        }
    }
}

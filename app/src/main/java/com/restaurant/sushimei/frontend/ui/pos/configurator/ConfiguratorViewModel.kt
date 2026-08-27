package com.restaurant.sushimei.frontend.ui.pos.configurator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestGroupDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestSelectionDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID

enum class QuoteState {
    NOT_REQUESTED,
    LOADING,
    VALID,
    INVALID, // Configuración local inválida (ej. minSelections no cumplido)
    ERROR
}


data class SelectionNode(
    val occurrenceId: String = UUID.randomUUID().toString(),
    val option: ConfigurationOptionDto,
    val nestedConfiguration: ConfigurationResponseDto? = null,
    val nestedSelections: Map<Long, List<SelectionNode>> = emptyMap(),
    val isLoadingNested: Boolean = false,
    val nestedError: String? = null
)

data class ConfiguratorUiState(
    val isLoadingConfig: Boolean = false,
    val configuration: ConfigurationResponseDto? = null,
    val defaultComponents: List<com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse> = emptyList(),
    val omittedComponentIds: Set<Long> = emptySet(),
    val note: String = "",
    val rootSelections: Map<Long, List<SelectionNode>> = emptyMap(),
    val quoteState: QuoteState = QuoteState.NOT_REQUESTED,
    val latestQuote: ItemQuoteResponseDto? = null,
    val errorMessage: String? = null,
    val generationToken: String = "", // For stale root detection
    val quoteRevision: String = "" // For stale quote protection
)

class ConfiguratorViewModel(
    private val menuRepository: IMenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfiguratorUiState())
    val uiState: StateFlow<ConfiguratorUiState> = _uiState.asStateFlow()

    private var quoteJob: Job? = null

    fun loadConfiguration(menuItemId: Long, existingConfig: com.restaurant.sushimei.frontend.data.model.ConfiguredProduct? = null) {
        quoteJob?.cancel()
        quoteJob = null

        val token = UUID.randomUUID().toString()
        _uiState.value = _uiState.value.copy(
            isLoadingConfig = true,
            errorMessage = null,
            configuration = null,
            latestQuote = null,
            rootSelections = emptyMap(),
            quoteState = QuoteState.NOT_REQUESTED,
            generationToken = token,
            quoteRevision = "",
            defaultComponents = emptyList(),
            omittedComponentIds = emptySet(),
            note = ""
        )

        viewModelScope.launch {
            try {
                kotlinx.coroutines.coroutineScope {
                    val configDeferred = async { menuRepository.getConfiguration(menuItemId) }
                    val componentsDeferred = async { menuRepository.getMenuItemComponents(menuItemId) }

                    val config = configDeferred.await()
                    val components = componentsDeferred.await()

                    if (_uiState.value.generationToken != token) return@coroutineScope

                    val initialRootSelections = mutableMapOf<Long, List<SelectionNode>>()
                    val initialOmitted = existingConfig?.omittedComponents?.map { it.id }?.toSet() ?: emptySet()
                    val initialNote = existingConfig?.note ?: ""

                    if (existingConfig != null) {
                        fun buildNodes(
                            configuredGroups: List<com.restaurant.sushimei.frontend.data.model.ConfiguredGroup>,
                            schemaGroups: List<ConfigurationGroupDto>
                        ): Map<Long, List<SelectionNode>> {
                            val resultMap = mutableMapOf<Long, List<SelectionNode>>()
                            configuredGroups.forEach { confGroup ->
                                val schemaGroup = schemaGroups.find { it.id == confGroup.groupId }
                                if (schemaGroup != null) {
                                    val nodes = confGroup.selections.mapNotNull { confSel ->
                                        val option = schemaGroup.options.find { it.menuItemId == confSel.menuItemId }
                                        if (option != null) {
                                            val copies = mutableListOf<SelectionNode>()
                                            repeat(confSel.quantity) {
                                                copies.add(
                                                    SelectionNode(
                                                        option = option,
                                                        nestedConfiguration = null,
                                                        nestedSelections = confSel.groups?.let { buildNodes(it, emptyList()) } ?: emptyMap()
                                                    )
                                                )
                                            }
                                            copies
                                        } else null
                                    }.flatten()
                                    resultMap[confGroup.groupId] = nodes
                                }
                            }
                            return resultMap
                        }

                        initialRootSelections.putAll(buildNodes(existingConfig.groups, config.groups))
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoadingConfig = false,
                        configuration = config,
                        defaultComponents = components,
                        rootSelections = initialRootSelections,
                        omittedComponentIds = initialOmitted,
                        note = initialNote
                    )
                    validateAndQuote()
                }
            } catch (e: Exception) {
                if (_uiState.value.generationToken != token) return@launch

                _uiState.value = _uiState.value.copy(
                    isLoadingConfig = false,
                    errorMessage = "Error al cargar la configuración: ${e.message}"
                )
            }
        }
    }

    fun addSelection(groupId: Long, option: ConfigurationOptionDto, parentOccurrenceId: String? = null) {
        val state = _uiState.value
        if (state.configuration == null) return

        val newNode = SelectionNode(option = option, isLoadingNested = option.requiresConfiguration)

        if (parentOccurrenceId == null) {
            val currentGroup = state.rootSelections[groupId]?.toMutableList() ?: mutableListOf()
            val groupConfig = state.configuration.groups.find { it.id == groupId } ?: return
            if (currentGroup.size >= groupConfig.maxSelections) return
            if (!groupConfig.allowDuplicates && currentGroup.any { it.option.menuItemId == option.menuItemId }) return

            currentGroup.add(newNode)
            val newRoot = state.rootSelections.toMutableMap()
            newRoot[groupId] = currentGroup
            _uiState.value = state.copy(rootSelections = newRoot)
        } else {
            val newRoot = state.rootSelections.toMutableMap()
            val updated = updateNodeInTree(newRoot, parentOccurrenceId) { parentNode ->
                val childConfig = parentNode.nestedConfiguration?.groups?.find { it.id == groupId } ?: return@updateNodeInTree parentNode
                val currentGroup = parentNode.nestedSelections[groupId]?.toMutableList() ?: mutableListOf()
                if (currentGroup.size >= childConfig.maxSelections) return@updateNodeInTree parentNode
                if (!childConfig.allowDuplicates && currentGroup.any { it.option.menuItemId == option.menuItemId }) return@updateNodeInTree parentNode

                currentGroup.add(newNode)
                val newNested = parentNode.nestedSelections.toMutableMap()
                newNested[groupId] = currentGroup
                parentNode.copy(nestedSelections = newNested)
            }
            if (updated) {
                _uiState.value = state.copy(rootSelections = newRoot)
            } else {
                return
            }
        }

        val token = _uiState.value.generationToken
        validateAndQuote()

        if (option.requiresConfiguration) {
            viewModelScope.launch {
                try {
                    val nestedConfig = menuRepository.getConfiguration(option.menuItemId)

                    if (_uiState.value.generationToken != token) return@launch

                    val currentRoot = _uiState.value.rootSelections.toMutableMap()
                    val didUpdate = updateNodeInTree(currentRoot, newNode.occurrenceId) { node ->
                        node.copy(isLoadingNested = false, nestedConfiguration = nestedConfig)
                    }

                    if (didUpdate) {
                        _uiState.value = _uiState.value.copy(rootSelections = currentRoot)
                        validateAndQuote()
                    }
                } catch (e: Exception) {
                    if (_uiState.value.generationToken != token) return@launch

                    val currentRoot = _uiState.value.rootSelections.toMutableMap()
                    val didUpdate = updateNodeInTree(currentRoot, newNode.occurrenceId) { node ->
                        node.copy(isLoadingNested = false, nestedError = e.message)
                    }
                    if (didUpdate) {
                        _uiState.value = _uiState.value.copy(rootSelections = currentRoot)
                        validateAndQuote()
                    }
                }
            }
        }
    }

    fun removeSelection(occurrenceId: String) {
        val currentRoot = _uiState.value.rootSelections.toMutableMap()
        if (removeNodeFromTree(currentRoot, occurrenceId)) {
            _uiState.value = _uiState.value.copy(rootSelections = currentRoot)
            validateAndQuote()
        }
    }

    private fun updateNodeInTree(
        selectionsMap: MutableMap<Long, List<SelectionNode>>,
        targetId: String,
        updater: (SelectionNode) -> SelectionNode
    ): Boolean {
        var found = false
        for ((groupId, list) in selectionsMap) {
            val newList = list.toMutableList()
            for (i in newList.indices) {
                val node = newList[i]
                if (node.occurrenceId == targetId) {
                    newList[i] = updater(node)
                    found = true
                } else {
                    val childSelections = node.nestedSelections.toMutableMap()
                    if (updateNodeInTree(childSelections, targetId, updater)) {
                        newList[i] = node.copy(nestedSelections = childSelections)
                        found = true
                    }
                }
            }
            if (found) {
                selectionsMap[groupId] = newList
                return true
            }
        }
        return false
    }

    private fun removeNodeFromTree(
        selectionsMap: MutableMap<Long, List<SelectionNode>>,
        targetId: String
    ): Boolean {
        for ((groupId, list) in selectionsMap) {
            val newList = list.toMutableList()
            val index = newList.indexOfFirst { it.occurrenceId == targetId }
            if (index != -1) {
                newList.removeAt(index)
                selectionsMap[groupId] = newList
                return true
            }

            var childModified = false
            for (i in newList.indices) {
                val childSelections = newList[i].nestedSelections.toMutableMap()
                if (removeNodeFromTree(childSelections, targetId)) {
                    newList[i] = newList[i].copy(nestedSelections = childSelections)
                    childModified = true
                    break
                }
            }
            if (childModified) {
                selectionsMap[groupId] = newList
                return true
            }
        }
        return false
    }

    private fun validateAndQuote() {
        quoteJob?.cancel()
        quoteJob = null

        val quoteRevision = UUID.randomUUID().toString()
        val state = _uiState.value
        val config = state.configuration ?: return

        val isValid = isTreeValid(config.groups, state.rootSelections)

        if (!isValid) {
            _uiState.value = state.copy(
                quoteRevision = quoteRevision,
                quoteState = QuoteState.INVALID,
                latestQuote = null,
                errorMessage = null
            )
            return
        }

        val requestGroups: List<ItemQuoteRequestGroupDto>
        try {
            requestGroups = buildRequestGroups(state.rootSelections)
        } catch (e: IllegalStateException) {
            _uiState.value = state.copy(
                quoteRevision = quoteRevision,
                quoteState = QuoteState.INVALID,
                latestQuote = null,
                errorMessage = e.message
            )
            return
        }

        val token = state.generationToken
        _uiState.value = _uiState.value.copy(
            quoteState = QuoteState.LOADING,
            quoteRevision = quoteRevision,
            errorMessage = null
        )

        quoteJob = viewModelScope.launch {
            delay(300)

            try {
                val request = ItemQuoteRequestDto(
                    quantity = 1,
                    groups = requestGroups,
                    omittedComponentIds = _uiState.value.omittedComponentIds.toList(),
                    note = _uiState.value.note.takeIf { it.isNotBlank() }
                )

                val quote = menuRepository.quoteItem(config.menuItemId, request)
                if (_uiState.value.generationToken == token && _uiState.value.quoteRevision == quoteRevision) {
                    _uiState.value = _uiState.value.copy(
                        quoteState = QuoteState.VALID,
                        latestQuote = quote
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.generationToken == token && _uiState.value.quoteRevision == quoteRevision) {
                    _uiState.value = _uiState.value.copy(
                        quoteState = QuoteState.ERROR,
                        errorMessage = "Error en la cotización: ${e.message}"
                    )
                }
            }
        }
    }

    private fun isTreeValid(
        groups: List<ConfigurationGroupDto>,
        selections: Map<Long, List<SelectionNode>>
    ): Boolean {
        for (group in groups) {
            val nodes = selections[group.id] ?: emptyList()
            if (nodes.size < group.minSelections || nodes.size > group.maxSelections) return false

            for (node in nodes) {
                if (node.isLoadingNested) return false
                if (node.nestedError != null) return false
                if (node.option.requiresConfiguration) {
                    val childConfig = node.nestedConfiguration ?: return false
                    if (!isTreeValid(childConfig.groups, node.nestedSelections)) return false
                }
            }
        }
        return true
    }

    private fun buildRequestGroups(selections: Map<Long, List<SelectionNode>>): List<ItemQuoteRequestGroupDto> {
        return selections.toSortedMap().map { (groupId, nodes) ->
            val byItem = nodes.groupBy { it.option.menuItemId }

                        val mappedSelections = byItem.toSortedMap().flatMap { (menuItemId, occurrences) ->
                val groupedByConfig = occurrences.groupBy { buildRequestGroups(it.nestedSelections) }
                groupedByConfig.map { (nested, sameConfigOccurrences) ->
                    ItemQuoteRequestSelectionDto(
                        menuItemId = menuItemId,
                        quantity = sameConfigOccurrences.size,
                        groups = nested
                    )
                }.sortedBy { it.menuItemId }
            }

            ItemQuoteRequestGroupDto(
                groupId = groupId,
                selections = mappedSelections
            )
        }
    }

    companion object {
        fun factory(menuRepository: IMenuRepository): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ConfiguratorViewModel(menuRepository) as T
                }
            }
    }

    fun toggleComponentOmission(componentId: Long) {
        val current = _uiState.value.omittedComponentIds.toMutableSet()
        if (current.contains(componentId)) {
            current.remove(componentId)
        } else {
            current.add(componentId)
        }
        _uiState.value = _uiState.value.copy(omittedComponentIds = current)
        validateAndQuote()
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
        validateAndQuote()
    }
}

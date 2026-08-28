package com.restaurant.sushimei.frontend.ui.admin.configurator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.*
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class DraftRule(
    val localId: String = UUID.randomUUID().toString(),
    val id: Long? = null,
    val targetType: SelectionRuleTargetType = SelectionRuleTargetType.ITEM,
    val targetId: Long? = null,
    val pricingPolicy: PricingPolicy = PricingPolicy.INCLUDED,
    val referencePrice: java.math.BigDecimal? = null,
    val fixedSurcharge: java.math.BigDecimal? = null,
    val priority: Int = 0,
    val active: Boolean = true,
    val version: Long = 0L
)

data class DraftGroup(
    val localId: String = UUID.randomUUID().toString(),
    val id: Long? = null,
    val name: String = "",
    val minSelections: Int = 0,
    val maxSelections: Int = 1,
    val allowDuplicates: Boolean = false,
    val displayOrder: Int = 0,
    val active: Boolean = true,
    val version: Long = 0L,
    val rules: List<DraftRule> = emptyList()
)

data class ConfigurationBuilderUiState(
    val isLoading: Boolean = false,
    val originalDefinition: MenuItemConfigurationDefinitionResponse? = null,
    val draftGroups: List<DraftGroup> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val availableItems: List<MenuItem> = emptyList(),
    val availableTags: List<CatalogTagDto> = emptyList()
)

class ConfigurationBuilderViewModel(
    private val repository: IMenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigurationBuilderUiState())
    val uiState: StateFlow<ConfigurationBuilderUiState> = _uiState

    private var currentMenuItemId: Long = 0L

    fun loadConfiguration(menuItemId: Long) {
        currentMenuItemId = menuItemId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                originalDefinition = null,
                draftGroups = emptyList(),
                availableTags = emptyList(),
                availableItems = emptyList(),
                isSaved = false
            )
            try {
                // Fetch dictionary data first. Log errors if any but don't fail immediately,
                // although the prompt says "No silencies errores de getTags(). Debe existir feedback/retry."
                // I will fetch them directly, if they fail it throws, triggering errorMessage.
                val tags = repository.getTags()
                val items = repository.getProducts()
                _uiState.value = _uiState.value.copy(availableTags = tags, availableItems = items)

                val def = repository.getMenuItemConfigurationDefinitionResponse(menuItemId)
                val activeGroups = def.groups.filter { it.group.active }.sortedBy { it.group.displayOrder }
                val draftGroups = activeGroups.map { gDef ->
                    val g = gDef.group
                    DraftGroup(
                        id = g.id,
                        name = g.name,
                        minSelections = g.minSelections,
                        maxSelections = g.maxSelections,
                        allowDuplicates = g.allowDuplicates,
                        displayOrder = g.displayOrder,
                        active = g.active,
                        version = g.version,
                        rules = gDef.rules.filter { it.active }.sortedBy { it.priority }.map { r ->
                            DraftRule(
                                id = r.id,
                                targetType = r.targetType,
                                targetId = r.targetId,
                                pricingPolicy = r.pricingPolicy,
                                referencePrice = r.referencePrice,
                                fixedSurcharge = r.fixedSurcharge,
                                priority = r.priority,
                                active = r.active,
                                version = r.version
                            )
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    originalDefinition = def,
                    draftGroups = draftGroups
                )
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No se encontró el ítem en el servidor (404)."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Error de red: ${e.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar: ${e.message}"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    fun addGroup(name: String, minSelections: Int, maxSelections: Int, allowDuplicates: Boolean, displayOrder: Int = 0) {
        val newGroup = DraftGroup(
            name = name,
            minSelections = minSelections,
            maxSelections = maxSelections,
            allowDuplicates = allowDuplicates,
            displayOrder = displayOrder,
            active = true,
            version = 0L
        )
        _uiState.value = _uiState.value.copy(draftGroups = _uiState.value.draftGroups + newGroup)
    }

    fun updateGroup(localId: String, name: String, minSelections: Int, maxSelections: Int, allowDuplicates: Boolean, displayOrder: Int) {
        val updated = _uiState.value.draftGroups.map { g ->
            if (g.localId == localId) {
                g.copy(name = name, minSelections = minSelections, maxSelections = maxSelections, allowDuplicates = allowDuplicates, displayOrder = displayOrder)
            } else g
        }
        _uiState.value = _uiState.value.copy(draftGroups = updated)
    }

    fun removeGroup(localId: String) {
        val updated = _uiState.value.draftGroups.filterNot { it.localId == localId }
        _uiState.value = _uiState.value.copy(draftGroups = updated)
    }

    fun addRule(groupLocalId: String, targetType: SelectionRuleTargetType, targetId: Long, pricingPolicy: PricingPolicy, referencePrice: java.math.BigDecimal?, fixedSurcharge: java.math.BigDecimal?, priority: Int) {
        val updated = _uiState.value.draftGroups.map { g ->
            if (g.localId == groupLocalId) {
                val newRule = DraftRule(
                    targetType = targetType,
                    targetId = targetId,
                    pricingPolicy = pricingPolicy,
                    referencePrice = referencePrice,
                    fixedSurcharge = fixedSurcharge,
                    priority = priority,
                    active = true,
                    version = 0L
                )
                g.copy(rules = g.rules + newRule)
            } else g
        }
        _uiState.value = _uiState.value.copy(draftGroups = updated)
    }

    fun updateRule(groupLocalId: String, ruleLocalId: String, targetType: SelectionRuleTargetType, targetId: Long, pricingPolicy: PricingPolicy, referencePrice: java.math.BigDecimal?, fixedSurcharge: java.math.BigDecimal?, priority: Int) {
        val updated = _uiState.value.draftGroups.map { g ->
            if (g.localId == groupLocalId) {
                val updatedRules = g.rules.map { r ->
                    if (r.localId == ruleLocalId) {
                        r.copy(targetType = targetType, targetId = targetId, pricingPolicy = pricingPolicy, referencePrice = referencePrice, fixedSurcharge = fixedSurcharge, priority = priority)
                    } else r
                }
                g.copy(rules = updatedRules)
            } else g
        }
        _uiState.value = _uiState.value.copy(draftGroups = updated)
    }

    fun removeRule(groupLocalId: String, ruleLocalId: String) {
        val updated = _uiState.value.draftGroups.map { g ->
            if (g.localId == groupLocalId) {
                g.copy(rules = g.rules.filterNot { it.localId == ruleLocalId })
            } else g
        }
        _uiState.value = _uiState.value.copy(draftGroups = updated)
    }

    private fun verifySemanticMatch(
        serverDef: MenuItemConfigurationDefinitionResponse,
        expectedDrafts: List<DraftGroup>
    ): Boolean {
        val expectedGroups = expectedDrafts.filter { it.active }
        if (expectedGroups.any { it.id == null }) return false

        val expectedGroupsById = expectedGroups.associateBy { it.id!! }
        val serverGroupsById = serverDef.groups
            .filter { it.group.active }
            .associateBy { it.group.id }

        if (expectedGroupsById.keys != serverGroupsById.keys) return false

        for ((groupId, expectedGroup) in expectedGroupsById) {
            val serverGroupDef = serverGroupsById[groupId] ?: return false
            val sg = serverGroupDef.group

            if (sg.name != expectedGroup.name ||
                sg.minSelections != expectedGroup.minSelections ||
                sg.maxSelections != expectedGroup.maxSelections ||
                sg.allowDuplicates != expectedGroup.allowDuplicates ||
                sg.displayOrder != expectedGroup.displayOrder ||
                sg.active != expectedGroup.active
            ) return false

            val expectedRules = expectedGroup.rules.filter { it.active }
            if (expectedRules.any { it.id == null }) return false

            val expectedRulesById = expectedRules.associateBy { it.id!! }
            val serverRulesById = serverGroupDef.rules
                .filter { it.active }
                .associateBy { it.id }

            if (expectedRulesById.keys != serverRulesById.keys) return false

            for ((ruleId, expectedRule) in expectedRulesById) {
                val sr = serverRulesById[ruleId] ?: return false

                if (sr.targetType != expectedRule.targetType ||
                    sr.targetId != expectedRule.targetId ||
                    sr.pricingPolicy != expectedRule.pricingPolicy ||
                    sr.priority != expectedRule.priority ||
                    sr.active != expectedRule.active
                ) return false

                val srRef = sr.referencePrice
                val erRef = expectedRule.referencePrice
                if (srRef == null && erRef != null) return false
                if (srRef != null && erRef == null) return false
                if (srRef != null && erRef != null && srRef.compareTo(erRef) != 0) return false

                val srSur = sr.fixedSurcharge
                val erSur = expectedRule.fixedSurcharge
                if (srSur == null && erSur != null) return false
                if (srSur != null && erSur == null) return false
                if (srSur != null && erSur != null && srSur.compareTo(erSur) != 0) return false
            }
        }
        return true
    }

    fun saveConfiguration() {
        val state = _uiState.value
        val itemId = currentMenuItemId
        if (itemId <= 0L || state.isSaving) return
        if (state.originalDefinition == null || state.isLoading || state.originalDefinition.menuItemId != itemId) {
            _uiState.value = state.copy(errorMessage = "No se puede guardar sin haber cargado la configuración original.")
            return
        }

        // 1. Validation
        for (group in state.draftGroups) {
            if (group.name.isBlank()) {
                _uiState.value = state.copy(errorMessage = "Nombre de grupo no puede estar vacío")
                return
            }
            if (group.name.length > 160) {
                _uiState.value = state.copy(errorMessage = "Nombre de grupo excede 160 caracteres en ${group.name}")
                return
            }
            if (group.minSelections < 0) {
                _uiState.value = state.copy(errorMessage = "Mínimo de selecciones debe ser >= 0 en ${group.name}")
                return
            }
            if (group.maxSelections < 1) {
                _uiState.value = state.copy(errorMessage = "Máximo de selecciones debe ser >= 1 en ${group.name}")
                return
            }
            if (group.maxSelections < group.minSelections) {
                _uiState.value = state.copy(errorMessage = "Máximo no puede ser menor al mínimo en ${group.name}")
                return
            }
            if (group.displayOrder < 0) {
                _uiState.value = state.copy(errorMessage = "Orden de display debe ser >= 0 en ${group.name}")
                return
            }
            for (rule in group.rules) {
                if (rule.targetId == null || rule.targetId <= 0) {
                    _uiState.value = state.copy(errorMessage = "Regla sin elemento de destino válido en ${group.name}")
                    return
                }
                if (rule.priority < 0) {
                    _uiState.value = state.copy(errorMessage = "Prioridad debe ser >= 0 en regla de ${group.name}")
                    return
                }
                if (rule.pricingPolicy == PricingPolicy.PRICE_DIFFERENCE && (rule.referencePrice == null || rule.referencePrice <= java.math.BigDecimal.ZERO)) {
                    _uiState.value = state.copy(errorMessage = "Falta precio de referencia positivo para PRICE_DIFFERENCE en regla de ${group.name}")
                    return
                }
                if (rule.pricingPolicy == PricingPolicy.FIXED_SURCHARGE && (rule.fixedSurcharge == null || rule.fixedSurcharge < java.math.BigDecimal.ZERO)) {
                    _uiState.value = state.copy(errorMessage = "Falta recargo fijo válido (>=0) para FIXED_SURCHARGE en regla de ${group.name}")
                    return
                }
                val checkMoney = { amount: java.math.BigDecimal?, name: String ->
                    if (amount != null) {
                        try {
                            val normalized = amount.setScale(2, java.math.RoundingMode.UNNECESSARY)
                            if (normalized.precision() > 19) {
                                throw IllegalArgumentException("$name excede precisión de 19")
                            }
                        } catch (e: ArithmeticException) {
                            throw IllegalArgumentException("$name no puede tener más de 2 decimales")
                        }
                    }
                }
                try {
                    if (rule.pricingPolicy == PricingPolicy.PRICE_DIFFERENCE) {
                        checkMoney(rule.referencePrice, "Precio")
                    } else if (rule.pricingPolicy == PricingPolicy.FIXED_SURCHARGE) {
                        checkMoney(rule.fixedSurcharge, "Recargo")
                    }
                } catch (e: Exception) {
                    _uiState.value = state.copy(errorMessage = e.message)
                    return
                }
            }
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                var draftGroups = _uiState.value.draftGroups
                var originalDefinition = _uiState.value.originalDefinition
                var originalGroups = originalDefinition?.groups ?: emptyList()

                fun updateOriginalGroupLocally(resp: MenuSelectionGroupResponse) {
                    val existing = originalGroups.find { it.group.id == resp.id }
                    if (existing != null) {
                        originalGroups = originalGroups.map { if (it.group.id == resp.id) it.copy(group = resp) else it }
                    } else {
                        originalGroups = originalGroups + MenuSelectionGroupDefinitionResponse(resp, emptyList())
                    }
                    originalDefinition = originalDefinition?.copy(groups = originalGroups)
                    _uiState.value = _uiState.value.copy(originalDefinition = originalDefinition)
                }

                fun archiveOriginalGroupLocally(groupId: Long) {
                    originalGroups = originalGroups.map {
                        if (it.group.id == groupId) it.copy(group = it.group.copy(active = false)) else it
                    }
                    originalDefinition = originalDefinition?.copy(groups = originalGroups)
                    _uiState.value = _uiState.value.copy(originalDefinition = originalDefinition)
                }

                fun updateOriginalRuleLocally(groupId: Long, resp: MenuSelectionRuleResponse) {
                    originalGroups = originalGroups.map { g ->
                        if (g.group.id == groupId) {
                            val existingRule = g.rules.find { it.id == resp.id }
                            val newRules = if (existingRule != null) {
                                g.rules.map { if (it.id == resp.id) resp else it }
                            } else {
                                g.rules + resp
                            }
                            g.copy(rules = newRules)
                        } else g
                    }
                    originalDefinition = originalDefinition?.copy(groups = originalGroups)
                    _uiState.value = _uiState.value.copy(originalDefinition = originalDefinition)
                }

                fun archiveOriginalRuleLocally(groupId: Long, ruleId: Long) {
                    originalGroups = originalGroups.map { g ->
                        if (g.group.id == groupId) {
                            g.copy(rules = g.rules.map { r -> if (r.id == ruleId) r.copy(active = false) else r })
                        } else g
                    }
                    originalDefinition = originalDefinition?.copy(groups = originalGroups)
                    _uiState.value = _uiState.value.copy(originalDefinition = originalDefinition)
                }

                // ARCHIVE removed groups
                val draftGroupIds = draftGroups.mapNotNull { it.id }.toSet()
                for (oGroup in originalGroups) {
                    if (oGroup.group.active && oGroup.group.id !in draftGroupIds) {
                        repository.deleteSelectionGroup(itemId, oGroup.group.id)
                        archiveOriginalGroupLocally(oGroup.group.id)
                    }
                }

                // Process groups and rules
                for (i in draftGroups.indices) {
                    val draftGroup = draftGroups[i]
                    var groupId = draftGroup.id

                    if (groupId == null) {
                        // POST Group
                        val req = CreateMenuSelectionGroupRequest(
                            name = draftGroup.name,
                            minSelections = draftGroup.minSelections,
                            maxSelections = draftGroup.maxSelections,
                            allowDuplicates = draftGroup.allowDuplicates,
                            displayOrder = draftGroup.displayOrder
                        )
                        val resp = repository.createSelectionGroup(itemId, req)
                        groupId = resp.id
                        draftGroups = updateGroupInDrafts(draftGroups, draftGroup.localId, resp)
                        updateOriginalGroupLocally(resp)
                        _uiState.value = _uiState.value.copy(draftGroups = draftGroups)
                    } else {
                        // PUT Group only if modified
                        val origGroup = originalGroups.find { it.group.id == groupId }?.group
                        if (origGroup != null && groupChanged(origGroup, draftGroup)) {
                            val req = UpdateMenuSelectionGroupRequest(
                                name = draftGroup.name,
                                minSelections = draftGroup.minSelections,
                                maxSelections = draftGroup.maxSelections,
                                allowDuplicates = draftGroup.allowDuplicates,
                                displayOrder = draftGroup.displayOrder,
                                active = draftGroup.active,
                                version = draftGroup.version
                            )
                            val resp = repository.updateSelectionGroup(itemId, groupId, req)
                            draftGroups = updateGroupInDrafts(draftGroups, draftGroup.localId, resp)
                            updateOriginalGroupLocally(resp)
                            _uiState.value = _uiState.value.copy(draftGroups = draftGroups)
                        }

                        // ARCHIVE removed rules
                        val origGroupDef = originalGroups.find { it.group.id == groupId }
                        val origRules = origGroupDef?.rules ?: emptyList()
                        val draftRuleIds = draftGroup.rules.mapNotNull { it.id }.toSet()
                        for (oRule in origRules) {
                            if (oRule.active && oRule.id !in draftRuleIds) {
                                repository.deleteSelectionRule(groupId, oRule.id)
                                archiveOriginalRuleLocally(groupId, oRule.id)
                            }
                        }
                    }

                    // Process rules for this group
                    val currentGroup = draftGroups.find { it.localId == draftGroup.localId }!!
                    val origRules = originalGroups.find { it.group.id == groupId }?.rules ?: emptyList()

                    for (rule in currentGroup.rules) {
                        val effectiveRef = if (rule.pricingPolicy == PricingPolicy.PRICE_DIFFERENCE) rule.referencePrice else null
                        val effectiveSurcharge = if (rule.pricingPolicy == PricingPolicy.FIXED_SURCHARGE) rule.fixedSurcharge else null

                        if (rule.id == null) {
                            // POST Rule
                            val rReq = CreateMenuSelectionRuleRequest(
                                targetType = rule.targetType,
                                targetId = rule.targetId!!,
                                pricingPolicy = rule.pricingPolicy,
                                referencePrice = effectiveRef,
                                fixedSurcharge = effectiveSurcharge,
                                priority = rule.priority
                            )
                            val rResp = repository.createSelectionRule(groupId!!, rReq)
                            draftGroups = updateRuleInDrafts(draftGroups, currentGroup.localId, rule.localId, rResp)
                            updateOriginalRuleLocally(groupId, rResp)
                            _uiState.value = _uiState.value.copy(draftGroups = draftGroups)
                        } else {
                            // PUT Rule only if modified
                            val origRule = origRules.find { it.id == rule.id }
                            if (origRule != null && ruleChanged(origRule, rule)) {
                                val rReq = UpdateMenuSelectionRuleRequest(
                                    targetType = rule.targetType,
                                    targetId = rule.targetId!!,
                                    pricingPolicy = rule.pricingPolicy,
                                    referencePrice = effectiveRef,
                                    fixedSurcharge = effectiveSurcharge,
                                    priority = rule.priority,
                                    active = rule.active,
                                    version = rule.version
                                )
                                val rResp = repository.updateSelectionRule(groupId!!, rule.id, rReq)
                                draftGroups = updateRuleInDrafts(draftGroups, currentGroup.localId, rule.localId, rResp)
                                updateOriginalRuleLocally(groupId, rResp)
                                _uiState.value = _uiState.value.copy(draftGroups = draftGroups)
                            }
                        }
                    }
                }

                // If we reach here, all network requests succeeded. Refetch.
                val verifiedDefinition = repository.getMenuItemConfigurationDefinitionResponse(itemId)

                val isSemanticMatch = verifySemanticMatch(verifiedDefinition, draftGroups)

                if (isSemanticMatch) {
                    // Map verified definition to drafts
                    val verifiedDrafts = verifiedDefinition.groups.filter { it.group.active }.map { groupDef ->
                        DraftGroup(
                            id = groupDef.group.id,
                            localId = java.util.UUID.randomUUID().toString(),
                            name = groupDef.group.name,
                            minSelections = groupDef.group.minSelections,
                            maxSelections = groupDef.group.maxSelections,
                            allowDuplicates = groupDef.group.allowDuplicates,
                            displayOrder = groupDef.group.displayOrder,
                            active = groupDef.group.active,
                            version = groupDef.group.version,
                            rules = groupDef.rules.filter { it.active }.map { ruleDef ->
                                DraftRule(
                                    id = ruleDef.id,
                                    localId = java.util.UUID.randomUUID().toString(),
                                    targetType = ruleDef.targetType,
                                    targetId = ruleDef.targetId,
                                    pricingPolicy = ruleDef.pricingPolicy,
                                    referencePrice = ruleDef.referencePrice,
                                    fixedSurcharge = ruleDef.fixedSurcharge,
                                    priority = ruleDef.priority,
                                    active = ruleDef.active,
                                    version = ruleDef.version
                                )
                            }
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        originalDefinition = verifiedDefinition,
                        draftGroups = verifiedDrafts,
                        isSaving = false,
                        isSaved = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        isSaved = false,
                        errorMessage = "La configuración guardada no coincide con la versión del servidor."
                    )
                }

            } catch (e: retrofit2.HttpException) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Error de red al guardar: ${e.code()}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Error al guardar: ${e.message}"
                )
            }
        }
    }

    private fun updateGroupInDrafts(drafts: List<DraftGroup>, localId: String, resp: MenuSelectionGroupResponse): List<DraftGroup> {
        return drafts.map {
            if (it.localId == localId) {
                it.copy(
                    id = resp.id,
                    name = resp.name,
                    minSelections = resp.minSelections,
                    maxSelections = resp.maxSelections,
                    allowDuplicates = resp.allowDuplicates,
                    displayOrder = resp.displayOrder,
                    active = resp.active,
                    version = resp.version
                )
            } else it
        }
    }

    private fun updateRuleInDrafts(drafts: List<DraftGroup>, groupLocalId: String, ruleLocalId: String, resp: MenuSelectionRuleResponse): List<DraftGroup> {
        return drafts.map { g ->
            if (g.localId == groupLocalId) {
                g.copy(rules = g.rules.map { r ->
                    if (r.localId == ruleLocalId) r.copy(
                        id = resp.id,
                        targetType = resp.targetType,
                        targetId = resp.targetId,
                        pricingPolicy = resp.pricingPolicy,
                        referencePrice = resp.referencePrice,
                        fixedSurcharge = resp.fixedSurcharge,
                        priority = resp.priority,
                        active = resp.active,
                        version = resp.version
                    ) else r
                })
            } else g
        }
    }

    private fun groupChanged(orig: MenuSelectionGroupResponse, draft: DraftGroup): Boolean {
        return orig.name != draft.name ||
               orig.minSelections != draft.minSelections ||
               orig.maxSelections != draft.maxSelections ||
               orig.allowDuplicates != draft.allowDuplicates ||
               orig.displayOrder != draft.displayOrder
    }

    private fun ruleChanged(orig: MenuSelectionRuleResponse, draft: DraftRule): Boolean {
        val effectiveRef = if (draft.pricingPolicy == PricingPolicy.PRICE_DIFFERENCE) draft.referencePrice else null
        val effectiveSurcharge = if (draft.pricingPolicy == PricingPolicy.FIXED_SURCHARGE) draft.fixedSurcharge else null
        val sameRef = (orig.referencePrice == null && effectiveRef == null) || (orig.referencePrice != null && effectiveRef != null && orig.referencePrice.compareTo(effectiveRef) == 0)
        val sameSur = (orig.fixedSurcharge == null && effectiveSurcharge == null) || (orig.fixedSurcharge != null && effectiveSurcharge != null && orig.fixedSurcharge.compareTo(effectiveSurcharge) == 0)
        return orig.targetType != draft.targetType ||
               orig.targetId != draft.targetId ||
               orig.pricingPolicy != draft.pricingPolicy ||
               orig.priority != draft.priority ||
               !sameRef ||
               !sameSur
    }
}

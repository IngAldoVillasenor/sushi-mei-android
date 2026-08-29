package com.restaurant.sushimei.frontend.ui.admin.configurator

import com.restaurant.sushimei.frontend.data.model.*
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigurationBuilderViewModelTest {

    private lateinit var repository: IMenuRepository
    private lateinit var viewModel: ConfigurationBuilderViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.getTags() } returns emptyList()
        coEvery { repository.getProducts() } returns emptyList()
        viewModel = ConfigurationBuilderViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createHttpException(code: Int): HttpException {
        return HttpException(Response.error<Any>(code, "".toResponseBody(null)))
    }

    private fun verifyZeroMutations() {
        coVerify(exactly = 0) { repository.createSelectionGroup(any(), any()) }
        coVerify(exactly = 0) { repository.updateSelectionGroup(any(), any(), any()) }
        coVerify(exactly = 0) { repository.deleteSelectionGroup(any(), any()) }
        coVerify(exactly = 0) { repository.createSelectionRule(any(), any()) }
        coVerify(exactly = 0) { repository.updateSelectionRule(any(), any(), any()) }
        coVerify(exactly = 0) { repository.deleteSelectionRule(any(), any()) }
    }

    @Test
    fun `initial GET fails causes zero mutation repository calls`() = runTest {
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } throws createHttpException(500)

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNotNull(viewModel.uiState.value.errorMessage)

        verifyZeroMutations()
    }

    @Test
    fun `stale item load failure preserves nothing and causes zero mutations`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        // Load item 1 successfully
        viewModel.loadConfiguration(1L)
        advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.originalDefinition?.menuItemId)

        // Load item 2 fails
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(2L) } throws createHttpException(500)
        viewModel.loadConfiguration(2L)
        advanceUntilIdle()

        // Assert state is cleared
        assertNull(viewModel.uiState.value.originalDefinition)
        assertTrue(viewModel.uiState.value.draftGroups.isEmpty())

        // Attempt save
        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNotNull(viewModel.uiState.value.errorMessage)

        verifyZeroMutations()
    }

    @Test
    fun `unchanged configuration - zero mutations`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()
        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        verifyZeroMutations()
    }

    @Test
    fun `new group and rule - POST exactly once`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        val gResp = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val rResp = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def
        coEvery { repository.createSelectionGroup(any(), any()) } returns gResp
        coEvery { repository.createSelectionRule(any(), any()) } returns rResp

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(gResp, listOf(rResp))))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.createSelectionGroup(1L, any()) }
        coVerify(exactly = 1) { repository.createSelectionRule(10L, any()) }
    }

    @Test
    fun `modified group and rule - PUT exactly once`() = runTest {
        val ruleDef = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)
        val groupDef = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), listOf(MenuSelectionGroupDefinitionResponse(groupDef, listOf(ruleDef))))

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        val updatedGroup = groupDef.copy(name = "G1 Mod", version = 2L)
        val updatedRule = ruleDef.copy(priority = 2, version = 2L)
        coEvery { repository.updateSelectionGroup(1L, 10L, any()) } returns updatedGroup
        coEvery { repository.updateSelectionRule(10L, 100L, any()) } returns updatedRule

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        val groupLocalId = viewModel.uiState.value.draftGroups[0].localId
        val ruleLocalId = viewModel.uiState.value.draftGroups[0].rules[0].localId
        viewModel.updateGroup(groupLocalId, "G1 Mod", 1, 1, false, 1)
        viewModel.updateRule(groupLocalId, ruleLocalId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 2)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(updatedGroup, listOf(updatedRule))))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.updateSelectionGroup(1L, 10L, any()) }
        coVerify(exactly = 1) { repository.updateSelectionRule(10L, 100L, any()) }
    }

    @Test
    fun `removed group and rule - DELETE exactly once`() = runTest {
        val ruleDef = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)
        val groupDef = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), listOf(MenuSelectionGroupDefinitionResponse(groupDef, listOf(ruleDef))))

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def
        coEvery { repository.deleteSelectionGroup(any(), any()) } returns Unit
        coEvery { repository.deleteSelectionRule(any(), any()) } returns Unit

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        val groupLocalId = viewModel.uiState.value.draftGroups[0].localId
        val ruleLocalId = viewModel.uiState.value.draftGroups[0].rules[0].localId

        viewModel.removeRule(groupLocalId, ruleLocalId)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(groupDef, emptyList())))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteSelectionRule(10L, 100L) }
        coVerify(exactly = 0) { repository.deleteSelectionGroup(1L, 10L) }

        val newGroupLocalId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.removeGroup(newGroupLocalId)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = emptyList())

        viewModel.saveConfiguration()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteSelectionGroup(1L, 10L) }
    }

    @Test
    fun `successful POST followed by later failure is not repeated on retry`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        val gResp = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        coEvery { repository.createSelectionGroup(any(), any()) } returns gResp

        val rResp = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)
        var callCount = 0
        coEvery { repository.createSelectionRule(any(), any()) } answers {
            if (callCount++ == 0) throw createHttpException(500) else rResp
        }

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1)

        viewModel.saveConfiguration()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaved)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(gResp, listOf(rResp))))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.createSelectionGroup(any(), any()) }
        coVerify(exactly = 2) { repository.createSelectionRule(any(), any()) }
    }

    @Test
    fun `successful PUT followed by later failure is not repeated on retry`() = runTest {
        val groupDef = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val groupDef2 = MenuSelectionGroupResponse(11L, 1L, "G2", 1, 1, false, 1, true, 1L, null, null)
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), listOf(
            MenuSelectionGroupDefinitionResponse(groupDef, emptyList()),
            MenuSelectionGroupDefinitionResponse(groupDef2, emptyList())
        ))
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        val groupDefMod1 = groupDef.copy(name = "G1 Mod", version = 2L)
        val groupDefMod2 = groupDef2.copy(name = "G2 Mod", version = 2L)
        coEvery { repository.updateSelectionGroup(1L, 10L, any()) } returns groupDefMod1

        var callCount = 0
        coEvery { repository.updateSelectionGroup(1L, 11L, any()) } answers {
            if (callCount++ == 0) throw createHttpException(500) else groupDefMod2
        }

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.updateGroup(viewModel.uiState.value.draftGroups[0].localId, "G1 Mod", 1, 1, false, 1)
        viewModel.updateGroup(viewModel.uiState.value.draftGroups[1].localId, "G2 Mod", 1, 1, false, 1)

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(
            MenuSelectionGroupDefinitionResponse(groupDefMod1, emptyList()),
            MenuSelectionGroupDefinitionResponse(groupDefMod2, emptyList())
        ))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.updateSelectionGroup(1L, 10L, any()) }
        coVerify(exactly = 2) { repository.updateSelectionGroup(1L, 11L, any()) }
    }

    @Test
    fun `successful DELETE followed by later failure is not repeated on retry`() = runTest {
        val groupDef = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val groupDef2 = MenuSelectionGroupResponse(11L, 1L, "G2", 1, 1, false, 1, true, 1L, null, null)
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), listOf(
            MenuSelectionGroupDefinitionResponse(groupDef, emptyList()),
            MenuSelectionGroupDefinitionResponse(groupDef2, emptyList())
        ))
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def
        coEvery { repository.deleteSelectionGroup(1L, 10L) } returns Unit

        var callCount = 0
        coEvery { repository.deleteSelectionGroup(1L, 11L) } answers {
            if (callCount++ == 0) throw createHttpException(500) else Unit
        }

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        val id1 = viewModel.uiState.value.draftGroups[0].localId
        val id2 = viewModel.uiState.value.draftGroups[1].localId
        viewModel.removeGroup(id1)
        viewModel.removeGroup(id2)

        viewModel.saveConfiguration()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaved)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = emptyList())

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.deleteSelectionGroup(1L, 10L) }
        coVerify(exactly = 2) { repository.deleteSelectionGroup(1L, 11L) }
    }

    @Test
    fun `successful rule PUT followed by later failure is not repeated on retry`() = runTest {
        val rule1 = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)
        val rule2 = MenuSelectionRuleResponse(101L, 10L, SelectionRuleTargetType.ITEM, 1001L, PricingPolicy.INCLUDED, null, null, 2, true, 1L, null, null)
        val groupDef = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), listOf(MenuSelectionGroupDefinitionResponse(groupDef, listOf(rule1, rule2))))

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        val rule1Mod = rule1.copy(priority = 3, version = 2L)
        val rule2Mod = rule2.copy(priority = 4, version = 2L)
        coEvery { repository.updateSelectionRule(10L, 100L, any()) } returns rule1Mod

        var callCount = 0
        coEvery { repository.updateSelectionRule(10L, 101L, any()) } answers {
            if (callCount++ == 0) throw createHttpException(500) else rule2Mod
        }

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        val groupLocalId = viewModel.uiState.value.draftGroups[0].localId
        val rule1LocalId = viewModel.uiState.value.draftGroups[0].rules[0].localId
        val rule2LocalId = viewModel.uiState.value.draftGroups[0].rules[1].localId

        viewModel.updateRule(groupLocalId, rule1LocalId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 3)
        viewModel.updateRule(groupLocalId, rule2LocalId, SelectionRuleTargetType.ITEM, 1001L, PricingPolicy.INCLUDED, null, null, 4)

        viewModel.saveConfiguration()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaved)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(groupDef, listOf(rule1Mod, rule2Mod))))
        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.updateSelectionRule(10L, 100L, any()) }
        coVerify(exactly = 2) { repository.updateSelectionRule(10L, 101L, any()) }
    }

    @Test
    fun `successful rule DELETE followed by later failure is not repeated on retry`() = runTest {
        val rule1 = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)
        val rule2 = MenuSelectionRuleResponse(101L, 10L, SelectionRuleTargetType.ITEM, 1001L, PricingPolicy.INCLUDED, null, null, 2, true, 1L, null, null)
        val groupDef = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), listOf(MenuSelectionGroupDefinitionResponse(groupDef, listOf(rule1, rule2))))

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def
        coEvery { repository.deleteSelectionRule(10L, 100L) } returns Unit

        var callCount = 0
        coEvery { repository.deleteSelectionRule(10L, 101L) } answers {
            if (callCount++ == 0) throw createHttpException(500) else Unit
        }

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        val groupLocalId = viewModel.uiState.value.draftGroups[0].localId
        val rule1LocalId = viewModel.uiState.value.draftGroups[0].rules[0].localId
        val rule2LocalId = viewModel.uiState.value.draftGroups[0].rules[1].localId

        viewModel.removeRule(groupLocalId, rule1LocalId)
        viewModel.removeRule(groupLocalId, rule2LocalId)

        viewModel.saveConfiguration()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaved)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(groupDef, emptyList())))
        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.deleteSelectionRule(10L, 100L) }
        coVerify(exactly = 2) { repository.deleteSelectionRule(10L, 101L) }
    }

    @Test
    fun `POST group succeeds, POST rule A succeeds, POST rule B fails is properly retried`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        val gResp = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        coEvery { repository.createSelectionGroup(any(), any()) } returns gResp

        val rRespA = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1, true, 1L, null, null)
        val rRespB = MenuSelectionRuleResponse(101L, 10L, SelectionRuleTargetType.ITEM, 1001L, PricingPolicy.INCLUDED, null, null, 2, true, 1L, null, null)

        coEvery { repository.createSelectionRule(10L, match { it.targetId == 1000L }) } returns rRespA
        var callCount = 0
        coEvery { repository.createSelectionRule(10L, match { it.targetId == 1001L }) } answers {
            if (callCount++ == 0) throw createHttpException(500) else rRespB
        }

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val groupLocalId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.addRule(groupLocalId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.INCLUDED, null, null, 1)
        viewModel.addRule(groupLocalId, SelectionRuleTargetType.ITEM, 1001L, PricingPolicy.INCLUDED, null, null, 2)

        viewModel.saveConfiguration()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaved)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(gResp, listOf(rRespA, rRespB))))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.createSelectionGroup(any(), any()) }
        coVerify(exactly = 1) { repository.createSelectionRule(10L, match { it.targetId == 1000L }) }
        coVerify(exactly = 2) { repository.createSelectionRule(10L, match { it.targetId == 1001L }) }
    }

    @Test
    fun `invalid local draft causes zero mutation calls`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, -1L, PricingPolicy.INCLUDED, null, null, 1) // Invalid

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNotNull(viewModel.uiState.value.errorMessage)

        verifyZeroMutations()
    }

    @Test
    fun `invalid money local draft causes zero mutation calls`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        // Invalid precision/scale for backend checkout money
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.FIXED_SURCHARGE, null, BigDecimal("79.999"), 1)

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNotNull(viewModel.uiState.value.errorMessage)

        verifyZeroMutations()
    }

    @Test
    fun `hydration updates drafts with authoritative server normalizations`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        val gResp = MenuSelectionGroupResponse(10L, 1L, "Elige 3 rollos", 1, 1, false, 1, true, 1L, null, null)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def
        coEvery { repository.createSelectionGroup(any(), any()) } returns gResp

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("  Elige 3 rollos  ", 1, 1, false, 1)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(gResp, emptyList())))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)

        // Final verification succeeded and draft name is normalized
        assertEquals("Elige 3 rollos", viewModel.uiState.value.draftGroups[0].name)
        assertEquals(10L, viewModel.uiState.value.draftGroups[0].id)
    }

    @Test
    fun `final GET returning mismatching definition sets isSaved to false`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        val gResp = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        coEvery { repository.createSelectionGroup(any(), any()) } returns gResp

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals("La configuración guardada no coincide con la versión del servidor.", viewModel.uiState.value.errorMessage)

        assertEquals(1, viewModel.uiState.value.draftGroups.size)
    }
    @Test
    fun `money with more than 2 decimal places is invalid - 79,999`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.FIXED_SURCHARGE, null, BigDecimal("79.999"), 1)

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNotNull(viewModel.uiState.value.errorMessage)
        verifyZeroMutations()
    }

    @Test
    fun `money at precision 19 boundary is valid - 99999999999999999,99`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        val gResp = MenuSelectionGroupResponse(10L, 1L, "G1", 1, 1, false, 1, true, 1L, null, null)
        val rResp = MenuSelectionRuleResponse(100L, 10L, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.FIXED_SURCHARGE, null, BigDecimal("99999999999999999.99"), 1, true, 1L, null, null)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def
        coEvery { repository.createSelectionGroup(any(), any()) } returns gResp
        coEvery { repository.createSelectionRule(any(), any()) } returns rResp

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.FIXED_SURCHARGE, null, BigDecimal("99999999999999999.99"), 1)

        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def.copy(groups = listOf(MenuSelectionGroupDefinitionResponse(gResp, listOf(rResp))))

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) { repository.createSelectionRule(any(), any()) }
    }

    @Test
    fun `money exceeding precision 19 after scale 2 is invalid - 999999999999999999`() = runTest {
        val def = MenuItemConfigurationDefinitionResponse(1L, "Test", 1L, emptyList(), emptyList())
        coEvery { repository.getMenuItemConfigurationDefinitionResponse(1L) } returns def

        viewModel.loadConfiguration(1L)
        advanceUntilIdle()

        viewModel.addGroup("G1", 1, 1, false, 1)
        val localId = viewModel.uiState.value.draftGroups[0].localId
        // 18 integer digits -> setScale(2) -> precision 20 -> invalid
        viewModel.addRule(localId, SelectionRuleTargetType.ITEM, 1000L, PricingPolicy.FIXED_SURCHARGE, null, BigDecimal("999999999999999999"), 1)

        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertNotNull(viewModel.uiState.value.errorMessage)
        verifyZeroMutations()
    }
}

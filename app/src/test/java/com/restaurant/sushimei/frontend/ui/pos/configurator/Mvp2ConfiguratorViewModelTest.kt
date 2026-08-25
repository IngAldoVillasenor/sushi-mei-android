package com.restaurant.sushimei.frontend.ui.pos.configurator

import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class Mvp2ConfiguratorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var menuRepository: IMenuRepository
    private lateinit var viewModel: ConfiguratorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        menuRepository = mockk(relaxed = true)
        viewModel = ConfiguratorViewModel(menuRepository)
    }

    private fun createBasicMockConfig(rootId: Long) = ConfigurationResponseDto(
        menuItemId = rootId, name = "Ramen", standaloneOrderable = true, basePrice = BigDecimal.TEN, requiresConfiguration = false, groups = emptyList()
    )
    private fun createQuote(total: BigDecimal) = ItemQuoteResponseDto(1L, "Ramen", 1, BigDecimal.TEN, total, emptyList(), BigDecimal.ZERO, total, total)

    @Test
    fun testToggleComponentOmission() = runTest {
        val rootId = 1L
        coEvery { menuRepository.getConfiguration(rootId) } returns createBasicMockConfig(rootId)
        coEvery { menuRepository.quoteItem(any(), any()) } returns createQuote(BigDecimal.TEN)

        viewModel.loadConfiguration(rootId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleComponentOmission(55L)
        assertTrue(viewModel.uiState.value.omittedComponentIds.contains(55L))

        viewModel.toggleComponentOmission(55L)
        assertFalse(viewModel.uiState.value.omittedComponentIds.contains(55L))
    }

    @Test
    fun testUpdateNoteMapsToQuoteRequest() = runTest {
        val rootId = 1L
        coEvery { menuRepository.getConfiguration(rootId) } returns createBasicMockConfig(rootId)
        val slot = slot<ItemQuoteRequestDto>()
        coEvery { menuRepository.quoteItem(rootId, capture(slot)) } returns createQuote(BigDecimal.TEN)

        viewModel.loadConfiguration(rootId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateNote("Extra spicy")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleComponentOmission(99L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(slot.isCaptured)
        val capturedRequest = slot.captured
        assertEquals("Extra spicy", capturedRequest.note)
        assertTrue(capturedRequest.omittedComponentIds.contains(99L))
    }

    @Test
    fun testLoadConfigurationFetchesBothConfigAndComponents() = runTest {
        val rootId = 1L
        coEvery { menuRepository.getConfiguration(rootId) } returns createBasicMockConfig(rootId)

        val arroz = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(1, "ARR", "Arroz", null, false, true, 1, false)
        val alga = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(2, "ALG", "Alga", null, true, true, 2, false)
        val pepino = com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(3, "PEP", "Pepino", null, true, true, 3, false)
        val mockComponents = listOf(arroz, alga, pepino)
        coEvery { menuRepository.getMenuItemComponents(rootId) } returns mockComponents
        coEvery { menuRepository.quoteItem(any(), any()) } returns createQuote(BigDecimal.TEN)

        viewModel.loadConfiguration(rootId)
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { menuRepository.getConfiguration(rootId) }
        io.mockk.coVerify(exactly = 1) { menuRepository.getMenuItemComponents(rootId) }

        val state = viewModel.uiState.value
        assertEquals(3, state.defaultComponents.size)
        assertTrue(state.defaultComponents.contains(arroz))
        assertTrue(state.defaultComponents.contains(alga))
        assertTrue(state.defaultComponents.contains(pepino))
    }

    @Test
    fun testLoadSecondMenuItemResetsState() = runTest {
        val firstId = 1L
        coEvery { menuRepository.getConfiguration(firstId) } returns createBasicMockConfig(firstId)
        coEvery { menuRepository.getMenuItemComponents(firstId) } returns listOf(
            com.restaurant.sushimei.frontend.data.model.DefaultComponentResponse(1, "ARR", "Arroz", null, false, true, 1, false)
        )
        coEvery { menuRepository.quoteItem(any(), any()) } returns createQuote(BigDecimal.TEN)

        viewModel.loadConfiguration(firstId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateNote("First note")
        viewModel.toggleComponentOmission(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("First note", viewModel.uiState.value.note)
        assertTrue(viewModel.uiState.value.omittedComponentIds.contains(1L))
        assertEquals(1, viewModel.uiState.value.defaultComponents.size)

        val secondId = 2L
        coEvery { menuRepository.getConfiguration(secondId) } returns createBasicMockConfig(secondId)
        coEvery { menuRepository.getMenuItemComponents(secondId) } returns emptyList()

        viewModel.loadConfiguration(secondId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.note)
        assertTrue(state.omittedComponentIds.isEmpty())
        assertTrue(state.defaultComponents.isEmpty())
    }

    @Test
    fun testComponentEndpointFailureProducesLoadError() = runTest {
        val rootId = 1L
        coEvery { menuRepository.getConfiguration(rootId) } returns createBasicMockConfig(rootId)
        coEvery { menuRepository.getMenuItemComponents(rootId) } throws Exception("Network error")

        viewModel.loadConfiguration(rootId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoadingConfig)
        assertEquals(null, state.configuration)
        assertTrue(state.errorMessage != null)
    }

}

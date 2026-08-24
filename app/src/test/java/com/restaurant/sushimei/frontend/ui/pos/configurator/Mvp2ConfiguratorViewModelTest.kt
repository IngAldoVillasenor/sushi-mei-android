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
}

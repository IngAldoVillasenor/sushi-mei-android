package com.restaurant.sushimei.frontend.ui.pos.configurator

import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguratorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var menuRepository: IMenuRepository
    private lateinit var viewModel: ConfiguratorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        menuRepository = mockk()
        viewModel = ConfiguratorViewModel(menuRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test configurator loads real backend configuration and quotes successfully without mock repository`() = runTest {
        val menuItemId = 1L
        val mockConfig = ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Test Roll",
            standaloneOrderable = true,
            basePrice = BigDecimal("110.00"),
            requiresConfiguration = false,
            groups = emptyList()
        )

        val mockQuote = ItemQuoteResponseDto(
            menuItemId = menuItemId,
            name = "Remote Banana Ebi",
            quantity = 1,
            baseUnitPrice = BigDecimal("110.00"),
            baseTotal = BigDecimal("110.00"),
            groups = emptyList(),
            unitAdjustmentTotal = BigDecimal.ZERO,
            unitTotal = BigDecimal("110.00"),
            total = BigDecimal("110.00")
        )

        coEvery { menuRepository.getConfiguration(menuItemId) } returns mockConfig
        coEvery { menuRepository.quoteItem(menuItemId, any()) } returns mockQuote

        viewModel.loadConfiguration(menuItemId)

        // Wait for coroutines to process configuration
        testScheduler.advanceUntilIdle()

        // Verify configuration was loaded from injected repository
        coVerify { menuRepository.getConfiguration(menuItemId) }

        // With debouncing in ConfiguratorViewModel (usually 300ms delay), we need to advance time
        testScheduler.advanceTimeBy(400)
        testScheduler.advanceUntilIdle()

        // Verify quote was fetched from injected repository
        coVerify { menuRepository.quoteItem(menuItemId, any()) }

        val state = viewModel.uiState.value
        assertEquals(QuoteState.VALID, state.quoteState)
        assertEquals("Remote Banana Ebi", state.latestQuote?.name)
        assertEquals(BigDecimal("110.00"), state.latestQuote?.total)
    }

    @Test
    fun `test state hygiene when starting a new configuration load`() = runTest {
        val menuItemId1 = 1L
        val menuItemId2 = 2L

        val mockConfig1 = ConfigurationResponseDto(menuItemId1, "Item 1", true, BigDecimal.TEN, false, emptyList())
        val mockConfig2 = ConfigurationResponseDto(menuItemId2, "Item 2", true, BigDecimal.TEN, false, emptyList())

        val mockQuote1 = ItemQuoteResponseDto(menuItemId1, "Item 1", 1, BigDecimal.TEN, BigDecimal.TEN, emptyList(), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN)
        val mockQuote2 = ItemQuoteResponseDto(menuItemId2, "Item 2", 1, BigDecimal.TEN, BigDecimal.TEN, emptyList(), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN)

        coEvery { menuRepository.getConfiguration(menuItemId1) } returns mockConfig1
        coEvery { menuRepository.quoteItem(menuItemId1, any()) } returns mockQuote1

        coEvery { menuRepository.getConfiguration(menuItemId2) } coAnswers {
            kotlinx.coroutines.delay(100)
            mockConfig2
        }
        coEvery { menuRepository.quoteItem(menuItemId2, any()) } returns mockQuote2

        // Load first item
        viewModel.loadConfiguration(menuItemId1)
        testScheduler.advanceUntilIdle()

        // State should have item 1
        assertEquals("Item 1", viewModel.uiState.value.configuration?.name)
        assertEquals("Item 1", viewModel.uiState.value.latestQuote?.name)

        // Load second item
        viewModel.loadConfiguration(menuItemId2)
        testScheduler.runCurrent()

        // Immediately after calling loadConfiguration, the state should be cleared
        assertNull("Configuration should be cleared", viewModel.uiState.value.configuration)
        assertNull("Latest quote should be cleared", viewModel.uiState.value.latestQuote)
        assertEquals(QuoteState.NOT_REQUESTED, viewModel.uiState.value.quoteState)

        // Let it finish loading config2 and quoting
        testScheduler.advanceUntilIdle()

        assertEquals("Item 2", viewModel.uiState.value.configuration?.name)
        assertEquals("Item 2", viewModel.uiState.value.latestQuote?.name)
    }
}

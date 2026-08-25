package com.restaurant.sushimei.frontend.ui.pos.configurator

import com.restaurant.sushimei.frontend.data.model.ConfigurationGroupDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationOptionDto
import com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestGroupDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestSelectionDto
import com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        io.mockk.coEvery { menuRepository.getMenuItemComponents(any()) } returns emptyList()
        viewModel = ConfiguratorViewModel(menuRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createBasicMockConfig(menuItemId: Long): ConfigurationResponseDto {
        return ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Test Roll",
            standaloneOrderable = true,
            basePrice = BigDecimal.ZERO,
            requiresConfiguration = false,
            groups = listOf(
                ConfigurationGroupDto(
                    id = 10,
                    name = "Options",
                    minSelections = 0,
                    maxSelections = 2,
                    allowDuplicates = true,
                    options = listOf(
                        ConfigurationOptionDto(
                            menuItemId = 100,
                            name = "Option 1",
                            category = "Test",
                            catalogPrice = BigDecimal("10.00"),
                            available = true,
                            priceAdjustment = BigDecimal.ZERO,
                            requiresConfiguration = false
                        ),
                        ConfigurationOptionDto(
                            menuItemId = 101,
                            name = "Option 2",
                            category = "Test",
                            catalogPrice = BigDecimal("15.00"),
                            available = true,
                            priceAdjustment = BigDecimal.ZERO,
                            requiresConfiguration = false
                        )
                    )
                )
            )
        )
    }

    private fun createConfigurableMockConfig(menuItemId: Long): ConfigurationResponseDto {
        return ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Complex Roll",
            standaloneOrderable = true,
            basePrice = BigDecimal.ZERO,
            requiresConfiguration = false,
            groups = listOf(
                ConfigurationGroupDto(
                    id = 10,
                    name = "Complex Options",
                    minSelections = 0,
                    maxSelections = 2,
                    allowDuplicates = true,
                    options = listOf(
                        ConfigurationOptionDto(
                            menuItemId = 100,
                            name = "Configurable Option",
                            category = "Test",
                            catalogPrice = BigDecimal("10.00"),
                            available = true,
                            priceAdjustment = BigDecimal.ZERO,
                            requiresConfiguration = true
                        )
                    )
                )
            )
        )
    }

    private fun createNestedMockConfig(menuItemId: Long, maxSelections: Int = 1): ConfigurationResponseDto {
        return ConfigurationResponseDto(
            menuItemId = menuItemId,
            name = "Nested Roll",
            standaloneOrderable = true,
            basePrice = BigDecimal.ZERO,
            requiresConfiguration = false,
            groups = listOf(
                ConfigurationGroupDto(
                    id = 20,
                    name = "Nested Options",
                    minSelections = 1,
                    maxSelections = maxSelections,
                    allowDuplicates = true,
                    options = listOf(
                        ConfigurationOptionDto(
                            menuItemId = 200,
                            name = "Nested A",
                            category = "Test",
                            catalogPrice = BigDecimal("5.00"),
                            available = true,
                            priceAdjustment = BigDecimal.ZERO,
                            requiresConfiguration = false
                        ),
                        ConfigurationOptionDto(
                            menuItemId = 201,
                            name = "Nested B",
                            category = "Test",
                            catalogPrice = BigDecimal("6.00"),
                            available = true,
                            priceAdjustment = BigDecimal.ZERO,
                            requiresConfiguration = false
                        )
                    )
                )
            )
        )
    }

    private fun createQuote(total: BigDecimal): ItemQuoteResponseDto {
        return ItemQuoteResponseDto(
            menuItemId = 1L,
            name = "Test",
            quantity = 1,
            baseUnitPrice = total,
            baseTotal = total,
            unitAdjustmentTotal = BigDecimal.ZERO,
            unitTotal = total,
            total = total
        )
    }

    @Test
    fun `configuration load + quote`() = runTest {
        val rootItemId = 1L
        val mockConfig = createBasicMockConfig(rootItemId)
        val mockQuote = createQuote(BigDecimal("100.00"))

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns mockQuote

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockConfig, viewModel.uiState.value.configuration)
        assertEquals(QuoteState.VALID, viewModel.uiState.value.quoteState)
        assertEquals(mockQuote, viewModel.uiState.value.latestQuote)
    }

    @Test
    fun `root state hygiene`() = runTest {
        val rootItemId = 1L
        val mockConfig = createBasicMockConfig(rootItemId)
        val mockQuote = createQuote(BigDecimal("100.00"))

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns mockQuote

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Load a different item
        val newRootItemId = 2L
        val newMockConfig = createBasicMockConfig(newRootItemId)
        coEvery { menuRepository.getConfiguration(newRootItemId) } returns newMockConfig
        coEvery { menuRepository.quoteItem(newRootItemId, any()) } returns createQuote(BigDecimal("200.00"))

        viewModel.loadConfiguration(newRootItemId)

        // Immediately after load, state should be reset
        assertNull(viewModel.uiState.value.configuration)
        assertEquals(QuoteState.NOT_REQUESTED, viewModel.uiState.value.quoteState)
        assertNull(viewModel.uiState.value.latestQuote)
        assertTrue(viewModel.uiState.value.rootSelections.isEmpty())

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(newMockConfig, viewModel.uiState.value.configuration)
        assertEquals(BigDecimal("200.00"), viewModel.uiState.value.latestQuote?.unitTotal)
    }

    @Test
    fun `recursive child load`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns createQuote(BigDecimal("10.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()

        val node = viewModel.uiState.value.rootSelections[10]?.find { it.occurrenceId == occ1 }
        assertEquals(nestedConfig, node?.nestedConfiguration)
    }

    @Test
    fun `incomplete nested config does not quote`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns createQuote(BigDecimal("10.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()
        clearMocks(menuRepository, answers = false)

        // Add configurable option. Its nested group requires 1 selection.
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(QuoteState.INVALID, viewModel.uiState.value.quoteState)
        coVerify(exactly = 0) { menuRepository.quoteItem(rootItemId, any()) }
    }

    @Test
    fun `completed nested config quotes`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns createQuote(BigDecimal("10.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()
        clearMocks(menuRepository, answers = false)

        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occ1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(QuoteState.VALID, viewModel.uiState.value.quoteState)
        coVerify(exactly = 1) { menuRepository.quoteItem(rootItemId, any()) }
    }

    @Test
    fun `recursive request structure`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig

        val quoteRequests = mutableListOf<ItemQuoteRequestDto>()
        coEvery { menuRepository.quoteItem(rootItemId, capture(quoteRequests)) } returns createQuote(BigDecimal("15.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occ1)
        testDispatcher.scheduler.advanceUntilIdle()

        val lastRequest = quoteRequests.last()
        val rootGroup = lastRequest.groups.find { it.groupId == 10L }!!
        val rootSelection = rootGroup.selections.find { it.menuItemId == 100L }!!
        assertEquals(1, rootSelection.quantity)

        val nestedGroup = rootSelection.groups.find { it.groupId == 20L }!!
        val nestedSelection = nestedGroup.selections.find { it.menuItemId == 200L }!!
        assertEquals(1, nestedSelection.quantity)
    }

    @Test
    fun `simple duplicate quantity=2`() = runTest {
        val rootItemId = 1L
        val mockConfig = createBasicMockConfig(rootItemId)

        val quoteRequests = mutableListOf<ItemQuoteRequestDto>()
        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.quoteItem(rootItemId, capture(quoteRequests)) } returns createQuote(BigDecimal("20.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        testDispatcher.scheduler.advanceUntilIdle()

        val lastRequest = quoteRequests.last()
        val rootGroup = lastRequest.groups.find { it.groupId == 10L }!!
        assertEquals(1, rootGroup.selections.size) // Only 1 selection entry

        val selection = rootGroup.selections[0]
        assertEquals(100L, selection.menuItemId)
        assertEquals(2, selection.quantity) // But with quantity 2
    }

    @Test
    fun `identical configurable duplicates quantity=2`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig

        val quoteRequests = mutableListOf<ItemQuoteRequestDto>()
        coEvery { menuRepository.quoteItem(rootItemId, capture(quoteRequests)) } returns createQuote(BigDecimal("30.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ2 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occ1)
        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occ2)
        testDispatcher.scheduler.advanceUntilIdle()

        val lastRequest = quoteRequests.last()
        val rootGroup = lastRequest.groups.find { it.groupId == 10L }!!
        assertEquals(1, rootGroup.selections.size)

        val selection = rootGroup.selections[0]
        assertEquals(100L, selection.menuItemId)
        assertEquals(2, selection.quantity)

        val nestedGroup = selection.groups.find { it.groupId == 20L }!!
        val nestedSelection = nestedGroup.selections.find { it.menuItemId == 200L }!!
        assertEquals(1, nestedSelection.quantity)
    }

    @Test
    fun `configurable duplicates with reordered child selections quote canonically`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L, maxSelections = 2)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig

        val quoteRequests = mutableListOf<ItemQuoteRequestDto>()
        coEvery { menuRepository.quoteItem(rootItemId, capture(quoteRequests)) } returns createQuote(BigDecimal("30.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occurrenceA = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occurrenceB = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occurrenceA)
        viewModel.addSelection(20, nestedConfig.groups[0].options[1], parentOccurrenceId = occurrenceA)
        viewModel.addSelection(20, nestedConfig.groups[0].options[1], parentOccurrenceId = occurrenceB)
        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occurrenceB)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 1) { menuRepository.quoteItem(rootItemId, any()) }
        assertEquals(QuoteState.VALID, viewModel.uiState.value.quoteState)
        assertNull(viewModel.uiState.value.errorMessage)

        val lastRequest = quoteRequests.last()
        assertEquals(listOf(10L), lastRequest.groups.map { it.groupId })
        val parentSelection = lastRequest.groups.single().selections.single()
        assertEquals(100L, parentSelection.menuItemId)
        assertEquals(2, parentSelection.quantity)
        assertEquals(listOf(20L), parentSelection.groups.map { it.groupId })

        val childSelections = parentSelection.groups.single().selections
        assertEquals(listOf(200L, 201L), childSelections.map { it.menuItemId })
        assertEquals(listOf(1, 1), childSelections.map { it.quantity })
    }

    @Test
    fun `divergent configurable duplicates rejected`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.getConfiguration(100L) } returns nestedConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns createQuote(BigDecimal("30.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ2 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()
        clearMocks(menuRepository, answers = false)

        viewModel.addSelection(20, nestedConfig.groups[0].options[0], parentOccurrenceId = occ1)
        viewModel.addSelection(20, nestedConfig.groups[0].options[1], parentOccurrenceId = occ2)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(QuoteState.INVALID, viewModel.uiState.value.quoteState)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("duplicadas"))
        coVerify(exactly = 0) { menuRepository.quoteItem(rootItemId, any()) }
    }

    @Test
    fun `occurrence-specific removal`() = runTest {
        val rootItemId = 1L
        val mockConfig = createBasicMockConfig(rootItemId)
        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns createQuote(BigDecimal("20.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        viewModel.addSelection(10, mockConfig.groups[0].options[1])
        val occ2 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.rootSelections[10]?.size)

        viewModel.removeSelection(occ1)
        testDispatcher.scheduler.advanceUntilIdle()

        val remaining = viewModel.uiState.value.rootSelections[10]!!
        assertEquals(1, remaining.size)
        assertEquals(occ2, remaining[0].occurrenceId)
    }

    @Test
    fun `late removed-child response cannot resurrect state`() = runTest {
        val rootItemId = 1L
        val mockConfig = createConfigurableMockConfig(rootItemId)
        val nestedConfig = createNestedMockConfig(100L)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig

        val childDeferred = CompletableDeferred<ConfigurationResponseDto>()
        coEvery { menuRepository.getConfiguration(100L) } coAnswers { childDeferred.await() }
        coEvery { menuRepository.quoteItem(rootItemId, any()) } returns createQuote(BigDecimal("10.00"))

        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.runCurrent() // starts the child load

        // Remove it before it finishes
        viewModel.removeSelection(occ1)
        testDispatcher.scheduler.runCurrent()

        // Finish the child load
        childDeferred.complete(nestedConfig)
        testDispatcher.scheduler.advanceUntilIdle()

        // Occ1 should not exist
        assertTrue(viewModel.uiState.value.rootSelections.isEmpty() || viewModel.uiState.value.rootSelections[10].isNullOrEmpty())
    }

    @Test
    fun `same-root stale quote protection`() = runTest {
        val rootItemId = 1L
        val mockConfig = createBasicMockConfig(rootItemId)

        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig

        // Use deterministic suspension for quotes
        val quoteADeferred = CompletableDeferred<ItemQuoteResponseDto>()
        val quoteBDeferred = CompletableDeferred<ItemQuoteResponseDto>()

        var quoteCount = 0
        coEvery { menuRepository.quoteItem(rootItemId, any()) } coAnswers {
            quoteCount++
            if (quoteCount == 1) {
                // Ignore the initial quote from loadConfiguration
                createQuote(BigDecimal.ZERO)
            } else if (quoteCount == 2) {
                // Quote A - non-cooperative, ignores cancellation
                withContext(NonCancellable) { quoteADeferred.await() }
            } else {
                // Quote B - non-cooperative, ignores cancellation
                withContext(NonCancellable) { quoteBDeferred.await() }
            }
        }

        // 1. Load one root configuration.
        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Produce a valid selection tree A.
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId // Option 1
        // 3. Start quote A. delay(300) exists, so advance time
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.runCurrent()
        // 4. Quote A remains suspended/in-flight.
        assertEquals(QuoteState.LOADING, viewModel.uiState.value.quoteState)

        // 5. Change the SAME root configuration to selection tree B before quote A completes.
        viewModel.addSelection(10, mockConfig.groups[0].options[1]) // Option 2
        // 6. Start quote B.
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.runCurrent()
        // 7. Quote B becomes the current valid quote (is suspended)
        assertEquals(QuoteState.LOADING, viewModel.uiState.value.quoteState)

        // 8. Allow quote A to finish late. Since it uses NonCancellable, it actually resumes and returns to the cancelled job.
        quoteADeferred.complete(createQuote(BigDecimal("10.00")))
        testDispatcher.scheduler.runCurrent()

        // 9. Assert quote A cannot replace state. Quote B is still loading.
        assertEquals(QuoteState.LOADING, viewModel.uiState.value.quoteState)
        assertEquals(BigDecimal.ZERO, viewModel.uiState.value.latestQuote?.unitTotal)

        // Finish quote B
        quoteBDeferred.complete(createQuote(BigDecimal("25.00")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(QuoteState.VALID, viewModel.uiState.value.quoteState)
        assertEquals(BigDecimal("25.00"), viewModel.uiState.value.latestQuote?.unitTotal)
    }

    @Test
    fun `valid to invalid tree invalidates previous quote late`() = runTest {
        val rootItemId = 1L
        // Basic config has Option 1, which we can select. minSelections for group 10 is 0. Wait, regression test says:
        // "configure a root group with minSelections >= 1"
        // Let's create a custom config for this test.
        val mockConfig = ConfigurationResponseDto(
            menuItemId = rootItemId,
            name = "Test Roll",
            standaloneOrderable = true,
            basePrice = BigDecimal.ZERO,
            requiresConfiguration = false,
            groups = listOf(
                ConfigurationGroupDto(
                    id = 10,
                    name = "Options",
                    minSelections = 1,
                    maxSelections = 2,
                    allowDuplicates = true,
                    options = listOf(
                        ConfigurationOptionDto(menuItemId = 100, name = "Opt 1", category = "Sides", catalogPrice = BigDecimal.ZERO, available = true, requiresConfiguration = false, priceAdjustment = BigDecimal.ZERO)
                    )
                )
            )
        )
        coEvery { menuRepository.getConfiguration(rootItemId) } returns mockConfig

        val quoteADeferred = CompletableDeferred<ItemQuoteResponseDto>()
        var quoteCount = 0
        coEvery { menuRepository.quoteItem(rootItemId, any()) } coAnswers {
            quoteCount++
            if (quoteCount == 1) {
                // initial quote shouldn't be valid yet because minSelections=1, so no quote actually starts!
                // Wait! If minSelections=1, loadConfiguration results in INVALID state, so NO initial quote is requested!
                withContext(NonCancellable) { quoteADeferred.await() }
            } else {
                withContext(NonCancellable) { quoteADeferred.await() }
            }
        }

        // 1. Load config
        viewModel.loadConfiguration(rootItemId)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(QuoteState.INVALID, viewModel.uiState.value.quoteState)

        // 2. Select Option A (makes it VALID)
        viewModel.addSelection(10, mockConfig.groups[0].options[0])
        val occ1 = viewModel.uiState.value.rootSelections[10]!!.last().occurrenceId
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.runCurrent()
        assertEquals(QuoteState.LOADING, viewModel.uiState.value.quoteState)

        // 3. Remove Option A (makes it INVALID)
        viewModel.removeSelection(occ1)
        testDispatcher.scheduler.runCurrent()

        assertEquals(QuoteState.INVALID, viewModel.uiState.value.quoteState)
        assertNull(viewModel.uiState.value.latestQuote)

        // 4. Resume Quote A late using NonCancellable mechanism
        quoteADeferred.complete(createQuote(BigDecimal("10.00")))
        testDispatcher.scheduler.runCurrent()

        // 5. Assert state is STILL INVALID and quote is null
        assertEquals(QuoteState.INVALID, viewModel.uiState.value.quoteState)
        assertNull(viewModel.uiState.value.latestQuote)
    }

    @Test
    fun `previous-root stale response protection`() = runTest {
        val rootA = 1L
        val rootB = 2L
        val configA = createBasicMockConfig(rootA)
        val configB = createBasicMockConfig(rootB)

        val quoteADeferred = CompletableDeferred<ItemQuoteResponseDto>()

        coEvery { menuRepository.getConfiguration(rootA) } returns configA
        coEvery { menuRepository.quoteItem(rootA, any()) } coAnswers { quoteADeferred.await() }

        coEvery { menuRepository.getConfiguration(rootB) } returns configB
        coEvery { menuRepository.quoteItem(rootB, any()) } returns createQuote(BigDecimal("200.00"))

        // root A begins/loads
        viewModel.loadConfiguration(rootA)
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.runCurrent()

        // root B becomes current
        viewModel.loadConfiguration(rootB)
        testDispatcher.scheduler.advanceUntilIdle() // quote for B finishes immediately

        assertEquals(configB, viewModel.uiState.value.configuration)
        assertEquals(QuoteState.VALID, viewModel.uiState.value.quoteState)
        assertEquals(BigDecimal("200.00"), viewModel.uiState.value.latestQuote?.unitTotal)

        // late root A response cannot overwrite B
        quoteADeferred.complete(createQuote(BigDecimal("100.00")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(configB, viewModel.uiState.value.configuration)
        assertEquals(BigDecimal("200.00"), viewModel.uiState.value.latestQuote?.unitTotal)
    }
}

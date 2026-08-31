package com.restaurant.sushimei.frontend.ui.menu

import com.restaurant.sushimei.frontend.data.api.VersionConflictException
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.MenuItemCreateRequestDto
import com.restaurant.sushimei.frontend.data.model.MenuItemResponse
import com.restaurant.sushimei.frontend.data.model.toDomain
import com.restaurant.sushimei.frontend.data.model.MenuItemUpdateRequestDto
import com.restaurant.sushimei.frontend.data.model.ItemPricingMode
import com.restaurant.sushimei.frontend.data.repository.IMenuRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MenuManagementViewModelTest {

    // =========================================================================
    // 5. An inactive item remains available to Menu Management and can be reactivated.
    // =========================================================================
    @Test
    fun `inactive items are visible and can be reactivated`() = runTest {
        val inactiveItem = makeMenuItem(99L, version = 1L, active = false)
        mockedProducts.add(inactiveItem)
        allProductsFlow.value = mockedProducts.toList()

        // Mock response for reactivating
        val response = makeResponse(99L, version = 2L, active = true)
        coEvery { repository.updateProduct(99L, any()) } answers {
            val updated = response.toDomain()
            allProductsFlow.value = allProductsFlow.value.map { if (it.id == 99L) updated else it }
            response
        }

        advanceUntilIdle()

        // Verify it's visible in Menu Management
        val state = viewModel.uiState.value as MenuManagementUiState.Success
        val visibleItem = state.filteredProducts.find { it.id == 99L }
        assertNotNull("Inactive item must be visible", visibleItem)
        assertFalse(visibleItem!!.activo)

        // Reactivate
        viewModel.selectProduct(visibleItem)
        viewModel.toggleActive(visibleItem, true)
        advanceUntilIdle()

        // Verify updated state
        val updatedState = viewModel.uiState.value as MenuManagementUiState.Success
        val updatedItem = updatedState.filteredProducts.find { it.id == 99L }
        assertTrue("Item should now be active", updatedItem?.activo == true)
        assertEquals(2L, updatedState.selectedProduct?.version)
    }


    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: IMenuRepository
    private lateinit var viewModel: MenuManagementViewModel

    private val allProductsFlow = MutableStateFlow<List<MenuItem>>(emptyList())
    private var mockedProducts = mutableListOf<MenuItem>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()

        coEvery { repository.observeAll() } returns allProductsFlow
        coEvery { repository.getProducts() } answers {
            allProductsFlow.value = mockedProducts.toList()
            mockedProducts
        }
        coEvery { repository.refreshCatalog(any(), any()) } answers {
            allProductsFlow.value = mockedProducts
        }

        viewModel = MenuManagementViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeMenuItem(id: Long, version: Long, active: Boolean = true) = MenuItem(
        id = id, nombre = "Roll $id", categoria = "Rolls", precio = BigDecimal("100"),
        activo = active, available = true, standaloneOrderable = true,
        displayOrder = 5, version = version
    )

    private fun makeResponse(id: Long, version: Long, active: Boolean = true) = MenuItemResponse(
        id = id, name = "Roll $id", description = "", category = "Rolls",
        price = BigDecimal("100"), active = active, available = true,
        standaloneOrderable = true, requiresConfiguration = false,
        pricingMode = ItemPricingMode.BASE_PLUS_ADJUSTMENTS, displayOrder = 5,
        tags = emptyList(), version = version, createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    // =========================================================================
    // 2. Existing product update sends the item's real current version.
    // 3. Preserves unrelated authoritative metadata when only price changes.
    // 9. Successful price change produces success state.
    // =========================================================================
    @Test
    fun `saveProduct sends correct version and metadata, produces success state`() = runTest {
        val originalItem = makeMenuItem(1L, version = 4L)
        val editedItem = originalItem.copy(precio = BigDecimal("120"))
        mockedProducts.add(originalItem)

        coEvery { repository.updateProduct(1L, any()) } returns makeResponse(1L, version = 5L)

        viewModel.selectProduct(editedItem)
        viewModel.saveProduct(editedItem)
        advanceUntilIdle()

        coVerify {
            repository.updateProduct(1L, match { req ->
                req.version == 4L &&
                req.price == BigDecimal("120") &&
                req.displayOrder == 5 &&
                req.available == true
            })
        }

        val state = viewModel.uiState.value as MenuManagementUiState.Success
        assertTrue(state.saveSuccess)
        coVerify(exactly = 1) { repository.refreshCatalog(any(), any()) } // Called once in init
        assertNull(state.saveError)
    }

    // =========================================================================
    // 4. Successful save uses the authoritative backend response.
    // 5. Consecutive saves use the newly returned version.
    // =========================================================================
    @Test
    fun `successful save uses authoritative response for consecutive saves`() = runTest {
        val originalItem = makeMenuItem(2L, version = 4L)
        mockedProducts.add(originalItem)

        // First save returns version 5
        coEvery { repository.updateProduct(2L, any()) } answers {
            val updated = makeMenuItem(2L, version = 5L)
            mockedProducts[0] = updated
            makeResponse(2L, version = 5L)
        }

        viewModel.saveProduct(originalItem.copy(precio = BigDecimal("89")))
        advanceUntilIdle()

        val state1 = viewModel.uiState.value as MenuManagementUiState.Success
        assertTrue(state1.saveSuccess)
        val updatedSelection = state1.selectedProduct
        assertNotNull(updatedSelection)
        assertEquals(5L, updatedSelection!!.version)

        // Second save must use version 5
        coEvery { repository.updateProduct(2L, any()) } answers {
            val updated2 = makeMenuItem(2L, version = 6L)
            mockedProducts[0] = updated2
            makeResponse(2L, version = 6L)
        }

        viewModel.saveProduct(updatedSelection.copy(precio = BigDecimal("99")))
        advanceUntilIdle()

        coVerify {
            repository.updateProduct(2L, match { req -> req.version == 5L && req.price == BigDecimal("99") })
        }
    }

    // =========================================================================
    // 6. Version conflict does not show success.
    // 7. Version conflict refreshes/reconciles with authoritative catalog state.
    // =========================================================================
    @Test
    fun `version conflict surfaces error and refreshes catalog`() = runTest {
        val originalItem = makeMenuItem(3L, version = 4L)
        mockedProducts.add(originalItem)

        coEvery { repository.updateProduct(3L, any()) } throws VersionConflictException()

        viewModel.selectProduct(originalItem)
        viewModel.saveProduct(originalItem.copy(precio = BigDecimal("150")))

        // Before advancing, simulate another user changed the server version to 5
        val updatedServerItem = makeMenuItem(3L, version = 5L).copy(precio = BigDecimal("180"))
        mockedProducts[0] = updatedServerItem

        advanceUntilIdle()

        val state = viewModel.uiState.value as MenuManagementUiState.Success
        assertFalse("Should not report success on conflict", state.saveSuccess)
        assertNotNull("Should contain error message", state.saveError)
        assertTrue(state.saveError!!.contains("modificado por otro usuario"))
        coVerify(exactly = 2) { repository.refreshCatalog(includeInactive = true) } // init + conflict


        // The form should be reconciled with the latest server data
        assertEquals(5L, state.selectedProduct?.version)
        assertEquals(BigDecimal("180"), state.selectedProduct?.precio)
    }

    // =========================================================================
    // 8. Generic repository/API failure produces visible error state.
    // =========================================================================
    @Test
    fun `generic API failure produces error state without losing catalog`() = runTest {
        val originalItem = makeMenuItem(4L, version = 1L)
        mockedProducts.add(originalItem)

        coEvery { repository.updateProduct(4L, any()) } throws RuntimeException("Network Error")

        viewModel.saveProduct(originalItem)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MenuManagementUiState.Success
        assertFalse(state.saveSuccess)
        assertNotNull(state.saveError)
        assertTrue(state.saveError!!.contains("Network Error"))
    }

    // =========================================================================
    // 10. Active/inactive toggle uses real current state.
    // 11. Active/inactive update uses optimistic locking correctly.
    // =========================================================================
    @Test
    fun `toggleActive sends current authoritative version and updates state`() = runTest {
        val originalItem = makeMenuItem(5L, version = 10L, active = true)
        mockedProducts.add(originalItem)

        coEvery { repository.updateProduct(5L, any()) } answers {
            val updated = originalItem.copy(activo = false, version = 11L)
            mockedProducts[0] = updated
            makeResponse(5L, version = 11L, active = false)
        }

        viewModel.selectProduct(originalItem)
        viewModel.toggleActive(originalItem, false)
        advanceUntilIdle()

        coVerify {
            repository.updateProduct(5L, match { req ->
                req.version == 10L &&
                req.active == false &&
                req.available == true
            })
        }

        val state = viewModel.uiState.value as MenuManagementUiState.Success
        assertEquals(11L, state.selectedProduct?.version)
        assertEquals(false, state.selectedProduct?.activo)
    }

    // =========================================================================
    // 12. A failed active/inactive update does not falsely change UI to success.
    // =========================================================================
    @Test
    fun `failed toggleActive surfaces error and reconciles`() = runTest {
        val originalItem = makeMenuItem(6L, version = 10L, active = true)
        mockedProducts.add(originalItem)

        coEvery { repository.updateProduct(6L, any()) } throws VersionConflictException()

        viewModel.selectProduct(originalItem)

        // Simulating server updated version and active = false in meantime
        mockedProducts[0] = originalItem.copy(version = 12L, activo = false)

        viewModel.toggleActive(originalItem, false)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MenuManagementUiState.Success
        assertNotNull(state.saveError)
        assertTrue(state.saveError!!.contains("modificado por otro usuario"))
        coVerify(exactly = 2) { repository.refreshCatalog(includeInactive = true) } // init + conflict

        // It should pull latest from server (version 12, false)
        assertEquals(12L, state.selectedProduct?.version)
        assertEquals(false, state.selectedProduct?.activo)
    }

    // =========================================================================
    // 13. A failed refresh handles gracefully.
    // =========================================================================
    @Test
    fun `version conflict surfaces error and handles refresh failure gracefully`() = runTest {
        val originalItem = makeMenuItem(7L, version = 4L)
        mockedProducts.add(originalItem)

        coEvery { repository.updateProduct(7L, any()) } throws VersionConflictException()
        coEvery { repository.refreshCatalog(any(), any()) } throws RuntimeException("Network down")

        viewModel.selectProduct(originalItem)
        viewModel.saveProduct(originalItem.copy(precio = BigDecimal("150")))

        advanceUntilIdle()

        val state = viewModel.uiState.value as MenuManagementUiState.Success
        assertFalse("Should not report success on conflict", state.saveSuccess)
        assertNotNull("Should contain error message", state.saveError)
        assertTrue(state.saveError!!.contains("no se pudieron obtener los datos"))

        // Should still keep the original selection to not crash or lose state arbitrarily
        assertEquals(4L, state.selectedProduct?.version)
    }
}

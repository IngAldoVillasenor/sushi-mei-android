package com.cardovia.merkon.app.ui

import com.cardovia.merkon.app.data.model.ApplicationRole
import com.cardovia.merkon.app.data.model.AuthenticatedUserDto
import com.cardovia.merkon.app.data.model.MenuItem
import com.cardovia.merkon.app.data.repository.IMenuRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class MainBootstrapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val menuRepo: IMenuRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { menuRepo.refreshCatalog(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun owner_emptyCatalog_setsEmptyState() = runTest {
        coEvery { menuRepo.observeAll() } returns flowOf(emptyList())
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.OWNER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        
        assertEquals(BootstrapState.Empty, viewModel.bootstrapState)
    }

    @Test
    fun owner_nonEmptyCatalog_setsConfiguredState() = runTest {
        val item = mockk<MenuItem>()
        coEvery { menuRepo.observeAll() } returns flowOf(listOf(item))
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.OWNER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        
        assertEquals(BootstrapState.Configured, viewModel.bootstrapState)
    }

    @Test
    fun owner_archivedOnlyCatalog_setsConfiguredState() = runTest {
        val item = mockk<MenuItem>()
        coEvery { menuRepo.observeAll() } returns flowOf(listOf(item))
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.OWNER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        
        assertEquals(BootstrapState.Configured, viewModel.bootstrapState)
    }

    @Test
    fun owner_errorFetching_setsErrorState() = runTest {
        coEvery { menuRepo.refreshCatalog(any(), any()) } throws Exception("Network error")
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.OWNER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        
        assertEquals(BootstrapState.Error, viewModel.bootstrapState)
    }

    @Test
    fun nonOwner_setsNotRequiredState() = runTest {
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.CASHIER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        
        assertEquals(BootstrapState.NotRequired, viewModel.bootstrapState)
    }

    @Test
    fun retry_recoversFromErrorToEmpty() = runTest {
        var callCount = 0
        coEvery { menuRepo.observeAll() } answers {
            if (callCount++ == 0) throw RuntimeException("Error")
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.OWNER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        assertEquals(BootstrapState.Error, viewModel.bootstrapState)
        
        viewModel.retry()
        
        assertEquals(BootstrapState.Empty, viewModel.bootstrapState)
    }

    @Test
    fun retry_recoversFromErrorToConfigured() = runTest {
        var callCount = 0
        coEvery { menuRepo.observeAll() } answers {
            if (callCount++ == 0) throw RuntimeException("Error")
            else kotlinx.coroutines.flow.flowOf(listOf(mockk(relaxed = true)))
        }
        val user = mockk<AuthenticatedUserDto>(relaxed = true) {
            io.mockk.every { role } returns ApplicationRole.OWNER
        }
        
        val viewModel = MainBootstrapViewModel(menuRepo, user)
        assertEquals(BootstrapState.Error, viewModel.bootstrapState)
        
        viewModel.retry()
        
        assertEquals(BootstrapState.Configured, viewModel.bootstrapState)
    }
}

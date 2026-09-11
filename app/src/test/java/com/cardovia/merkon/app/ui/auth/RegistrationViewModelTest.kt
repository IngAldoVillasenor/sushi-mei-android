package com.cardovia.merkon.app.ui.auth

import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.PublicMerkonApi
import com.cardovia.merkon.app.data.model.GenericMessageResponseDto
import com.cardovia.merkon.app.ui.screens.RegistrationViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val api: PublicMerkonApi = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun isPasswordValid_validPassword_returnsTrue() {
        val viewModel = RegistrationViewModel(api)
        viewModel.email = "test@test.com"
        viewModel.displayName = "Test User"
        viewModel.password = "ValidPassword123!"
        assertTrue(viewModel.isPasswordValid)
    }

    @Test
    fun isPasswordValid_containsMerkon_returnsFalse() {
        val viewModel = RegistrationViewModel(api)
        viewModel.email = "test@test.com"
        viewModel.password = "thisismerkon12345"
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun isPasswordValid_containsSushiMei_returnsFalse() {
        val viewModel = RegistrationViewModel(api)
        viewModel.email = "test@test.com"
        viewModel.password = "thisissushimei12345"
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun isPasswordValid_containsEmail_returnsFalse() {
        val viewModel = RegistrationViewModel(api)
        viewModel.email = "test@test.com"
        viewModel.password = "mypasswordtest@test.com"
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun isFormValid_businessNameLength160_returnsTrue() {
        val viewModel = io.mockk.spyk(RegistrationViewModel(api))
        io.mockk.every { viewModel.isEmailValid } returns true
        viewModel.email = "test@test.com"
        viewModel.displayName = "Test User"
        viewModel.businessName = "a".repeat(160)
        viewModel.password = "ValidPassword123!"
        viewModel.confirmPassword = "ValidPassword123!"
        viewModel.termsAccepted = true
        assertTrue(viewModel.isFormValid)
    }

    @Test
    fun isFormValid_businessNameOver160_returnsFalse() {
        val viewModel = io.mockk.spyk(RegistrationViewModel(api))
        io.mockk.every { viewModel.isEmailValid } returns true
        viewModel.email = "test@test.com"
        viewModel.displayName = "Test User"
        viewModel.businessName = "a".repeat(161)
        viewModel.password = "ValidPassword123!"
        viewModel.confirmPassword = "ValidPassword123!"
        viewModel.termsAccepted = true
        assertFalse(viewModel.isFormValid)
    }

    @Test
    fun register_success_clearsSecrets() = runTest {
        coEvery { api.register(any()) } returns Response.success(202, GenericMessageResponseDto("OK"))
        val viewModel = RegistrationViewModel(api)
        viewModel.email = "test@test.com"
        viewModel.displayName = "Test"
        viewModel.businessName = "Business"
        viewModel.password = "ValidPassword123!"
        viewModel.confirmPassword = "ValidPassword123!"
        viewModel.termsAccepted = true

        val result = viewModel.register()

        assertTrue(result)
        assertEquals("", viewModel.password)
        assertEquals("", viewModel.confirmPassword)
    }

    @Test
    fun register_authPasswordRejected_setsErrorMessage() = runTest {
        coEvery { api.register(any()) } throws ApiException("AUTH_PASSWORD_REJECTED", "Password rejected", 400, "123")
        val viewModel = RegistrationViewModel(api)
        viewModel.email = "test@test.com"

        val result = viewModel.register()

        assertFalse(result)
        assertEquals("La contraseña no cumple con los requisitos de seguridad.", viewModel.errorMessage)
    }

    @Test
    fun register_rateLimited_setsErrorMessage() = runTest {
        coEvery { api.register(any()) } throws ApiException("RATE_LIMIT", "Rate limit", 429, "123")
        val viewModel = RegistrationViewModel(api)

        val result = viewModel.register()

        assertFalse(result)
        assertEquals("Demasiados intentos. Por favor, espera antes de intentar de nuevo.", viewModel.errorMessage)
    }
}

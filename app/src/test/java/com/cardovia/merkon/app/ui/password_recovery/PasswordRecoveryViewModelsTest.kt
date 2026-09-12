package com.cardovia.merkon.app.ui.password_recovery

import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.PublicMerkonApi
import com.cardovia.merkon.app.data.model.GenericMessageResponseDto
import com.cardovia.merkon.app.data.model.PasswordRecoveryConfirmRequestDto
import com.cardovia.merkon.app.data.model.PasswordRecoveryRequestDto
import com.cardovia.merkon.app.ui.screens.password_recovery.PasswordRecoveryRequestViewModel
import com.cardovia.merkon.app.ui.screens.password_recovery.PasswordResetViewModel
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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
class PasswordRecoveryViewModelsTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: PublicMerkonApi

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        api = mockk()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // --- PasswordRecoveryRequestViewModel ---

    @Test
    fun request_success_setsIsSuccess_andTrimsEmail() = runTest {
        val requestSlot = slot<PasswordRecoveryRequestDto>()
        coEvery { api.requestPasswordRecovery(capture(requestSlot)) } returns Response.success(
            202, GenericMessageResponseDto("Accepted")
        )
        val vm = PasswordRecoveryRequestViewModel(api)
        vm.emailInput = "  test@example.com  "
        vm.submitRequest()
        assertTrue(vm.isSuccess)
        assertEquals("test@example.com", requestSlot.captured.email)
    }

    @Test
    fun request_invalidRequest_showsMessage() = runTest {
        coEvery { api.requestPasswordRecovery(any()) } throws ApiException(code = "PASSWORD_RECOVERY_INVALID_REQUEST", message = "", httpStatus = 400)
        val vm = PasswordRecoveryRequestViewModel(api)
        vm.emailInput = "test"
        vm.submitRequest()
        assertFalse(vm.isSuccess)
        assertEquals("La solicitud de recuperación no es válida.", vm.errorMessage)
    }

    @Test
    fun request_rateLimited_showsMessage() = runTest {
        coEvery { api.requestPasswordRecovery(any()) } throws ApiException(code = "PASSWORD_RECOVERY_RATE_LIMITED", message = "", httpStatus = 429)
        val vm = PasswordRecoveryRequestViewModel(api)
        vm.emailInput = "test@example.com"
        vm.submitRequest()
        assertFalse(vm.isSuccess)
        assertEquals("Demasiados intentos. Por favor, espera antes de intentar de nuevo.", vm.errorMessage)
    }

    @Test
    fun request_http429_showsMessage() = runTest {
        coEvery { api.requestPasswordRecovery(any()) } throws ApiException(code = "GENERIC_LIMIT", message = "", httpStatus = 429)
        val vm = PasswordRecoveryRequestViewModel(api)
        vm.emailInput = "test@example.com"
        vm.submitRequest()
        assertFalse(vm.isSuccess)
        assertEquals("Demasiados intentos. Por favor, espera antes de intentar de nuevo.", vm.errorMessage)
    }

    @Test
    fun request_networkFailure_showsMessage() = runTest {
        coEvery { api.requestPasswordRecovery(any()) } throws Exception("Network Error")
        val vm = PasswordRecoveryRequestViewModel(api)
        vm.emailInput = "test@example.com"
        vm.submitRequest()
        assertFalse(vm.isSuccess)
        assertEquals("Error de conexión.", vm.errorMessage)
    }

    // --- PasswordResetViewModel ---

    @Test
    fun reset_success_invokesSuccessCallback() = runTest {
        val requestSlot = slot<PasswordRecoveryConfirmRequestDto>()
        coEvery { api.confirmPasswordRecovery(capture(requestSlot)) } returns Response.success<Void>(204, null)
        val vm = PasswordResetViewModel(api)
        vm.password = "NewPass123!"
        vm.confirmPassword = "NewPass123!"

        var successCalled = false
        var invalidTokenCalled = false

        vm.submitReset(
            token = "token-123",
            onSuccessAction = { successCalled = true },
            onInvalidTokenAction = { invalidTokenCalled = true }
        )

        assertTrue(vm.isSuccess)
        assertTrue(successCalled)
        assertFalse(invalidTokenCalled)
        assertEquals("token-123", requestSlot.captured.token)
        assertEquals("NewPass123!", requestSlot.captured.newPassword)
        assertEquals("", vm.password) // passwords cleared
    }

    @Test
    fun reset_mismatch_showsError() = runTest {
        val vm = PasswordResetViewModel(api)
        vm.password = "NewPass123!"
        vm.confirmPassword = "Different!"
        vm.submitReset("token-123", {}, {})
        assertFalse(vm.isSuccess)
        assertEquals("Las contraseñas no coinciden.", vm.errorMessage)
    }

    @Test
    fun reset_invalidToken_invokesInvalidTokenCallback() = runTest {
        coEvery { api.confirmPasswordRecovery(any()) } throws ApiException(code = "PASSWORD_RECOVERY_INVALID_TOKEN", message = "", httpStatus = 400)
        val vm = PasswordResetViewModel(api)
        vm.password = "NewPass123!"
        vm.confirmPassword = "NewPass123!"

        var successCalled = false
        var invalidTokenCalled = false

        vm.submitReset(
            token = "bad-token",
            onSuccessAction = { successCalled = true },
            onInvalidTokenAction = { invalidTokenCalled = true }
        )

        assertFalse(vm.isSuccess)
        assertTrue(vm.isInvalidToken)
        assertFalse(successCalled)
        assertTrue(invalidTokenCalled)
        assertEquals("El enlace de restablecimiento no es válido o ha vencido.", vm.errorMessage)
        assertEquals("", vm.password) // passwords cleared
    }

    @Test
    fun reset_authRejected_showsError() = runTest {
        coEvery { api.confirmPasswordRecovery(any()) } throws ApiException(code = "AUTH_PASSWORD_REJECTED", message = "", httpStatus = 400)
        val vm = PasswordResetViewModel(api)
        vm.password = "weak"
        vm.confirmPassword = "weak"
        vm.submitReset("token-123", {}, {})
        assertFalse(vm.isSuccess)
        assertEquals("La contraseña no cumple con los requisitos de seguridad.", vm.errorMessage)
    }

    @Test
    fun reset_networkFailure_showsMessage() = runTest {
        coEvery { api.confirmPasswordRecovery(any()) } throws Exception("Network Error")
        val vm = PasswordResetViewModel(api)
        vm.password = "NewPass123!"
        vm.confirmPassword = "NewPass123!"
        vm.submitReset("token-123", {}, {})
        assertFalse(vm.isSuccess)
        assertEquals("Error de conexión.", vm.errorMessage)
    }
}

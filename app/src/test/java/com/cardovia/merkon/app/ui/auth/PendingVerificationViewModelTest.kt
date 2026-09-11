package com.cardovia.merkon.app.ui.auth

import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.PublicMerkonApi
import com.cardovia.merkon.app.data.model.GenericMessageResponseDto
import com.cardovia.merkon.app.ui.screens.PendingVerificationViewModel
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
class PendingVerificationViewModelTest {

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
    fun verify_success200_reachesVerifiedState_and_clearsToken() = runTest {
        coEvery { api.verifyEmail(any()) } returns Response.success(200, GenericMessageResponseDto("OK"))
        val viewModel = PendingVerificationViewModel(api)
        viewModel.tokenInput = "some-token"

        viewModel.verify("some-token")

        assertTrue(viewModel.isVerified)
        assertEquals("", viewModel.tokenInput)
    }

    @Test
    fun verify_clearsTokenBeforeApiCall() = runTest {
        var tokenDuringCall: String? = null
        val viewModel = PendingVerificationViewModel(api)

        coEvery { api.verifyEmail(any()) } answers {
            // Observe the tokenInput exactly when the mock API is invoked
            tokenDuringCall = viewModel.tokenInput
            Response.success(200, GenericMessageResponseDto("OK"))
        }

        viewModel.tokenInput = "some-token"
        viewModel.verify("some-token")

        // Assert it was empty *during* the call
        assertEquals("", tokenDuringCall)
        // Assert it remains empty *after* the call
        assertEquals("", viewModel.tokenInput)
    }

    @Test
    fun verify_invalidTokenException_setsErrorMessage_and_remainsCleared() = runTest {
        coEvery { api.verifyEmail(any()) } throws ApiException("EMAIL_VERIFICATION_INVALID_TOKEN", "Invalid", 400, "123")
        val viewModel = PendingVerificationViewModel(api)
        viewModel.tokenInput = "bad-token"

        viewModel.verify("bad-token")

        assertFalse(viewModel.isVerified)
        assertEquals("", viewModel.tokenInput) // token remains cleared
        assertTrue(viewModel.errorMessage?.contains("inválido") == true || viewModel.errorMessage?.contains("inv\\u00e1lido") == true || viewModel.errorMessage?.contains("invǭlido") == true)
    }

    @Test
    fun verify_ioException_setsErrorMessage_and_remainsCleared() = runTest {
        coEvery { api.verifyEmail(any()) } throws java.io.IOException("Network error")
        val viewModel = PendingVerificationViewModel(api)
        viewModel.tokenInput = "network-token"

        viewModel.verify("network-token")

        assertFalse(viewModel.isVerified)
        assertEquals("", viewModel.tokenInput) // token remains cleared
        assertTrue(viewModel.errorMessage?.contains("conex") == true)
    }

    @Test
    fun resend_success202_setsConditionalMessage() = runTest {
        coEvery { api.resendVerification(any()) } returns Response.success(202, GenericMessageResponseDto("OK"))
        val viewModel = PendingVerificationViewModel(api)

        viewModel.resend("test@test.com")

        assertTrue(viewModel.successMessage?.contains("requiere") == true)
    }

    @Test
    fun resend_rateLimited_setsErrorMessage() = runTest {
        coEvery { api.resendVerification(any()) } throws ApiException("EMAIL_VERIFICATION_RESEND_RATE_LIMITED", "Limit", 429, "123")
        val viewModel = PendingVerificationViewModel(api)

        viewModel.resend("test@test.com")

        assertTrue(viewModel.errorMessage?.contains("Demasiados reenv") == true)
    }

    @Test
    fun reset_clearsAllStateIncludingEmailAndLoading() = runTest {
        val viewModel = PendingVerificationViewModel(api)
        viewModel.tokenInput = "token123"
        viewModel.emailInput = "test@example.com"
        viewModel.isLoading = true
        viewModel.errorMessage = "Error"
        viewModel.successMessage = "Success"
        viewModel.isVerified = true

        viewModel.reset()

        assertEquals("", viewModel.tokenInput)
        assertEquals("", viewModel.emailInput)
        assertEquals(false, viewModel.isLoading)
        assertEquals(null, viewModel.errorMessage)
        assertEquals(null, viewModel.successMessage)
        assertEquals(false, viewModel.isVerified)
    }

    @Test
    fun syncExternalEmail_setsEmailOrClearsIfNull() = runTest {
        val viewModel = PendingVerificationViewModel(api)

        viewModel.syncExternalEmail("test@example.com")
        assertEquals("test@example.com", viewModel.emailInput)

        viewModel.syncExternalEmail(null)
        assertEquals("", viewModel.emailInput)
    }
}
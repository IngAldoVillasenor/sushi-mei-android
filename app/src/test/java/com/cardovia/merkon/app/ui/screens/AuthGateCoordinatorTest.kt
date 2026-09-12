package com.cardovia.merkon.app.ui.screens

import com.cardovia.merkon.app.data.model.DeepLinkEvent
import com.cardovia.merkon.app.data.repository.AuthRepository
import com.cardovia.merkon.app.data.repository.AuthState
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthGateCoordinatorTest {

    @Test
    fun testLoginToForgotPassword() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        assertNull(coordinator.activeGateState)

        coordinator.activeGateState = "forgot_password"
        assertEquals("forgot_password", coordinator.activeGateState)
    }

    @Test
    fun testValidResetDeepLink_routesToResetFlow() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        val event = DeepLinkEvent.PasswordReset("token")
        coordinator.onNewDeepLinkEvent(event, AuthState.Unauthenticated)

        assertEquals("password_reset", coordinator.activeGateState)
        assertEquals(false, consumed)
    }

    @Test
    fun testResetDeepLink_whileAuthenticated_overridesMainScreen() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        val event = DeepLinkEvent.PasswordReset("token")
        coordinator.onNewDeepLinkEvent(event, AuthState.Authenticated(mockk(relaxed = true)))

        assertEquals("password_reset", coordinator.activeGateState)
        assertEquals(false, consumed)
    }

    @Test
    fun testPasswordResetSuccess_clearsSessionAndConsumesToken() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        coordinator.onPasswordResetSuccess()

        verify { authRepo.clearSession() }
        assertEquals(true, consumed)
        assertEquals("password_reset_success", coordinator.activeGateState)
    }

    @Test
    fun testSuccessAcknowledgement_canReturnToLogin() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        coordinator.activeGateState = "password_reset_success"
        coordinator.onPasswordResetBackToLogin()

        assertEquals(true, consumed)
        verify { authRepo.clearSession() }
        assertNull(coordinator.activeGateState)
    }

    @Test
    fun testInvalidToken_discardsEventAndRemainsActive() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        coordinator.onPasswordResetInvalidToken()

        assertEquals(true, consumed)
        assertEquals("password_reset_invalid", coordinator.activeGateState)
    }

    @Test
    fun testInvalidToken_requestNewLink_entersRequestScreen() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        coordinator.activeGateState = "password_reset_invalid"
        coordinator.onPasswordResetNavigateToRequest()

        assertEquals(true, consumed)
        verify { authRepo.clearSession() }
        assertEquals("forgot_password", coordinator.activeGateState)
    }

    @Test
    fun testInvalidToken_returnToLogin_clearsGateState() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        coordinator.activeGateState = "password_reset_invalid"
        coordinator.onPasswordResetBackToLogin()

        assertEquals(true, consumed)
        verify { authRepo.clearSession() }
        assertNull(coordinator.activeGateState)
    }

    @Test
    fun testInvalidToken_newValidResetEvent_overridesState() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        coordinator.activeGateState = "password_reset_invalid"

        val newEvent = DeepLinkEvent.PasswordReset("new_token")
        coordinator.onNewDeepLinkEvent(newEvent, AuthState.Unauthenticated)

        assertEquals("password_reset", coordinator.activeGateState)
    }
    @Test
    fun testVerificationDeepLink_initializing_doesNotConsume() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        val event = DeepLinkEvent.Verification("token")
        coordinator.onNewDeepLinkEvent(event, AuthState.Initializing)

        assertEquals(false, consumed)
    }

    @Test
    fun testVerificationDeepLink_authenticated_consumesEvent() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        val event = DeepLinkEvent.Verification("token")
        coordinator.onNewDeepLinkEvent(event, AuthState.Authenticated(mockk(relaxed = true)))

        assertEquals(true, consumed)
    }

    @Test
    fun testVerificationDeepLink_unauthenticated_doesNotConsume() {
        val authRepo: AuthRepository = mockk(relaxed = true)
        var consumed = false
        val coordinator = AuthGateCoordinator(authRepo) { consumed = true }

        val event = DeepLinkEvent.Verification("token")
        coordinator.onNewDeepLinkEvent(event, AuthState.Unauthenticated)

        assertEquals(false, consumed)
    }
}

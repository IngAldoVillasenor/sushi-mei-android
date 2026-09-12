package com.cardovia.merkon.app.data.model

enum class ApplicationRole {
    OWNER,
    MANAGER,
    CASHIER,
    KITCHEN
}

data class AuthenticatedUserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: ApplicationRole,
    val active: Boolean,
    val version: Long
)

data class AuthResponseDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val sessionExpiresAt: String,
    val user: AuthenticatedUserDto
)

data class LoginRequestDto(
    val username: String,
    val password: String,
    val deviceId: String,
    val deviceName: String?,
    val appVersion: String?
)

data class RefreshRequestDto(
    val refreshToken: String,
    val deviceId: String
)

data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String
)

data class SessionDto(
    val id: String,
    val deviceId: String,
    val deviceName: String?,
    val appVersion: String?,
    val createdAt: String,
    val lastRefreshedAt: String,
    val absoluteExpiresAt: String,
    val revokedAt: String?,
    val revokeReason: String?,
    val current: Boolean
)

data class UserCreateRequestDto(
    val username: String,
    val displayName: String,
    val password: String,
    val role: ApplicationRole
)

data class UserUpdateRequestDto(
    val displayName: String,
    val role: ApplicationRole,
    val active: Boolean,
    val version: Long
)

data class UserResetPasswordRequestDto(
    val newPassword: String,
    val version: Long
)

// ============================================================================
// Public Registration & Verification
// ============================================================================

data class RegistrationRequestDto(
    val email: String,
    val displayName: String,
    val password: String,
    val businessName: String,
    val termsAccepted: Boolean
)

data class GenericMessageResponseDto(
    val message: String
)

data class VerifyEmailRequestDto(
    val token: String
)

data class ResendVerificationRequestDto(
    val email: String
)


// ============================================================================
// Password Recovery
// ============================================================================

data class PasswordRecoveryRequestDto(
    val email: String
)

data class PasswordRecoveryConfirmRequestDto(
    val token: String,
    val newPassword: String
)

sealed class DeepLinkEvent {
    data class Verification(val token: String) : DeepLinkEvent()
    data class PasswordReset(val token: String) : DeepLinkEvent()
}

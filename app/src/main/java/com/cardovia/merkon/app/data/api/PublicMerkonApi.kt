package com.cardovia.merkon.app.data.api

import com.cardovia.merkon.app.data.model.AuthResponseDto
import com.cardovia.merkon.app.data.model.LoginRequestDto
import com.cardovia.merkon.app.data.model.RefreshRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PublicMerkonApi {
    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<AuthResponseDto>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): Response<AuthResponseDto>

    @POST("/api/v1/registration")
    suspend fun register(@Body request: com.cardovia.merkon.app.data.model.RegistrationRequestDto): Response<com.cardovia.merkon.app.data.model.GenericMessageResponseDto>

    @POST("/api/v1/registration/email-verification/verify")
    suspend fun verifyEmail(@Body request: com.cardovia.merkon.app.data.model.VerifyEmailRequestDto): Response<com.cardovia.merkon.app.data.model.GenericMessageResponseDto>

    @POST("/api/v1/registration/email-verification/resend")
    suspend fun resendVerification(@Body request: com.cardovia.merkon.app.data.model.ResendVerificationRequestDto): Response<com.cardovia.merkon.app.data.model.GenericMessageResponseDto>

    @POST("/api/v1/auth/password-recovery/request")
    suspend fun requestPasswordRecovery(@Body request: com.cardovia.merkon.app.data.model.PasswordRecoveryRequestDto): Response<com.cardovia.merkon.app.data.model.GenericMessageResponseDto>

    @POST("/api/v1/auth/password-recovery/confirm")
    suspend fun confirmPasswordRecovery(@Body request: com.cardovia.merkon.app.data.model.PasswordRecoveryConfirmRequestDto): Response<Void>
}

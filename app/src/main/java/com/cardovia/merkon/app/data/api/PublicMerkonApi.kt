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
}

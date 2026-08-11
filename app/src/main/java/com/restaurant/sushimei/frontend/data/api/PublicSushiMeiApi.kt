package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.AuthResponseDto
import com.restaurant.sushimei.frontend.data.model.LoginRequestDto
import com.restaurant.sushimei.frontend.data.model.RefreshRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PublicSushiMeiApi {
    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<AuthResponseDto>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): Response<AuthResponseDto>
}

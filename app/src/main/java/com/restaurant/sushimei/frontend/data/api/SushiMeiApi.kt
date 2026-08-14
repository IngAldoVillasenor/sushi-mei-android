package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface SushiMeiApi {
    // ============================================================================
    // FASE 6S2: Authenticated Self & Security
    // ============================================================================

    @POST("/api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("/api/v1/auth/me")
    suspend fun getMe(): Response<AuthenticatedUserDto>

    @POST("/api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): Response<Unit>

    @GET("/api/v1/auth/sessions")
    suspend fun getSessions(): Response<List<SessionDto>>

    @DELETE("/api/v1/auth/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String): Response<Unit>

    @GET("/api/v1/security/users")
    suspend fun getUsers(): Response<List<AuthenticatedUserDto>>

    @GET("/api/v1/security/users/{id}")
    suspend fun getUser(@Path("id") id: Long): Response<AuthenticatedUserDto>

    @POST("/api/v1/security/users")
    suspend fun createUser(@Body request: UserCreateRequestDto): Response<AuthenticatedUserDto>

    @PUT("/api/v1/security/users/{id}")
    suspend fun updateUser(
        @Path("id") id: Long,
        @Body request: UserUpdateRequestDto
    ): Response<AuthenticatedUserDto>

    @POST("/api/v1/security/users/{id}/reset-password")
    suspend fun resetUserPassword(
        @Path("id") id: Long,
        @Body request: UserResetPasswordRequestDto
    ): Response<Unit>

    @GET("/api/v1/security/users/{id}/sessions")
    suspend fun getUserSessions(@Path("id") id: Long): Response<List<SessionDto>>

    @DELETE("/api/v1/security/sessions/{id}")
    suspend fun revokeUserSession(@Path("id") id: String): Response<Unit>

    // ============================================================================
    // ORDERS (Kitchen)
    // ============================================================================

    @GET("/api/orders/active")
    suspend fun getActiveOrders(): Response<List<OrderRecord>>

    @GET("/api/v1/orders/active")
    suspend fun getOperationalActiveOrders(): Response<List<OperationalOrderSummaryDto>>

    @GET("/api/v1/orders/{id}")
    suspend fun getOperationalOrderDetail(@Path("id") id: Long): Response<OperationalOrderDetailDto>

    @PUT("/api/orders/{id}/ready")
    suspend fun markOrderReady(@Path("id") orderId: Long): Response<Unit>

    @PUT("/api/orders/{id}/prepare")
    suspend fun acceptAndPrepareOrder(@Path("id") orderId: Long): Response<Unit>

    @POST("/api/orders/{id}/reject")
    suspend fun rejectOrder(
        @Path("id") orderId: Long,
        @Body request: RejectRequest
    ): Response<Unit>

    @PUT("/api/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") orderId: Long): Response<Unit>

    @PUT("/api/orders/{id}/validate-payment")
    suspend fun validatePayment(@Path("id") orderId: Long): Response<Unit>

    // ============================================================================
    // ORDERS (POS / Phase 6B)
    // ============================================================================

    @POST("/api/v1/orders")
    suspend fun createOrder(
        @Body request: ManualPosOrderRequest
    ): Response<ManualPosOrderResponse>

    // ============================================================================
    // FASE 6A2: Operational Catalog & Configuration
    // ============================================================================

    @GET("/api/v1/menu/items")
    suspend fun getMenuItems(
        @Query("standaloneOnly") standaloneOnly: Boolean? = null
    ): Response<List<MenuItemResponse>>

    @GET("/api/v1/menu/items/{id}")
    suspend fun getMenuItem(@Path("id") id: Long): Response<MenuItemResponse>

    @POST("/api/v1/menu/items")
    suspend fun createMenuItem(@Body request: MenuItemCreateRequestDto): Response<MenuItemResponse>

    @PUT("/api/v1/menu/items/{id}")
    suspend fun updateMenuItem(
        @Path("id") id: Long,
        @Body request: MenuItemUpdateRequestDto
    ): Response<MenuItemResponse>

    @DELETE("/api/v1/menu/items/{id}")
    suspend fun deleteMenuItem(@Path("id") id: Long): Response<Unit>

    @GET("/api/v1/menu/items/{id}/configuration")
    suspend fun getMenuItemConfiguration(@Path("id") id: Long): Response<ConfigurationResponseDto>

    @POST("/api/v1/menu/items/{id}/quote")
    suspend fun quoteMenuItem(
        @Path("id") id: Long,
        @Body request: com.restaurant.sushimei.frontend.data.model.ItemQuoteRequestDto
    ): retrofit2.Response<com.restaurant.sushimei.frontend.data.model.ItemQuoteResponseDto>

    // ============================================================================
    // FASE 6A2: Tags Management
    // ============================================================================

    @GET("/api/v1/menu/tags")
    suspend fun getTags(
        @Query("includeInactive") includeInactive: Boolean = false
    ): Response<List<CatalogTagDto>>

    @POST("/api/v1/menu/tags")
    suspend fun createTag(@Body request: TagCreateRequestDto): Response<CatalogTagDto>

    @PUT("/api/v1/menu/tags/{id}")
    suspend fun updateTag(
        @Path("id") id: Long,
        @Body request: TagUpdateRequestDto
    ): Response<CatalogTagDto>

    @DELETE("/api/v1/menu/tags/{id}")
    suspend fun deleteTag(@Path("id") id: Long): Response<Unit>

    @PUT("/api/v1/menu/items/{id}/tags")
    suspend fun updateItemTags(
        @Path("id") id: Long,
        @Body request: ItemTagsUpdateRequestDto
    ): Response<Unit>

    // ============================================================================
    // FASE 6A2: Configuration Definition (Admin)
    // ============================================================================

    // TODO: Add these DTOs to ApiModels if not present yet, or use generic ones
    // For now we assume they return generic objects or we can define them later as needed by Admin

    // ============================================================================
    // FASE 6A3: Temporal Promotion Engine
    // ============================================================================

    @GET("/api/v1/promotions")
    suspend fun getPromotions(
        @Query("includeInactive") includeInactive: Boolean = false
    ): Response<List<PromotionResponseDto>>

    @GET("/api/v1/promotions/{id}")
    suspend fun getPromotion(@Path("id") id: Long): Response<PromotionResponseDto>

    @POST("/api/v1/promotions")
    suspend fun createPromotion(@Body request: PromotionCreateRequestDto): Response<PromotionResponseDto>

    @PUT("/api/v1/promotions/{id}")
    suspend fun updatePromotion(
        @Path("id") id: Long,
        @Body request: PromotionUpdateRequestDto
    ): Response<PromotionResponseDto>

    @DELETE("/api/v1/promotions/{id}")
    suspend fun deletePromotion(@Path("id") id: Long): Response<Unit>

    // Note: The /promotions/quote is global, quoting a whole cart
    @POST("/api/v1/promotions/quote")
    suspend fun quotePromotions(
        @Body request: QuoteRequestDto
    ): Response<QuoteResponseDto>
}

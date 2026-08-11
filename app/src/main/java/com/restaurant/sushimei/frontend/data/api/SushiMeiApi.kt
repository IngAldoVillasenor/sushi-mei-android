package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface SushiMeiApi {

    // ============================================================================
    // ORDERS (Kitchen)
    // ============================================================================

    @GET("/api/orders/active")
    suspend fun getActiveOrders(): Response<List<OrderRecord>>

    @PUT("/api/orders/{id}/prepare")
    suspend fun acceptAndPrepareOrder(@Path("id") orderId: Long): Response<ResponseBody>

    @POST("/api/orders/{id}/reject")
    suspend fun rejectOrder(
        @Path("id") orderId: Long,
        @Body request: RejectRequest
    ): Response<ResponseBody>

    @PUT("/api/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") orderId: Long): Response<ResponseBody>

    @PUT("/api/orders/{id}/validate-payment")
    suspend fun validatePayment(@Path("id") orderId: Long): Response<ResponseBody>

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
    suspend fun deleteMenuItem(@Path("id") id: Long): Response<ResponseBody>

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
    suspend fun deleteTag(@Path("id") id: Long): Response<ResponseBody>

    @PUT("/api/v1/menu/items/{id}/tags")
    suspend fun updateItemTags(
        @Path("id") id: Long,
        @Body request: ItemTagsUpdateRequestDto
    ): Response<ResponseBody>

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
    suspend fun deletePromotion(@Path("id") id: Long): Response<ResponseBody>

    // Note: The /promotions/quote is global, quoting a whole cart
    @POST("/api/v1/promotions/quote")
    suspend fun quotePromotions(
        @Body request: QuoteRequestDto
    ): Response<QuoteResponseDto>
}
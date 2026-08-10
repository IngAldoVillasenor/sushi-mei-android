package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.OrderRecord
import com.restaurant.sushimei.frontend.data.model.RejectRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

interface SushiMeiApi {

    // 1. Obtener todas las órdenes que acaban de caer
    @GET("/api/orders/active")
    suspend fun getActiveOrders(): Response<List<OrderRecord>>

    // 2. Aceptar orden (La tablet manda a imprimir y cambia a PREPARING)
    @PUT("/api/orders/{id}/prepare")
    suspend fun acceptAndPrepareOrder(@Path("id") orderId: Long): Response<ResponseBody>

    // 3. Rechazar orden por falta de insumos
    @POST("/api/orders/{id}/reject")
    suspend fun rejectOrder(
        @Path("id") orderId: Long,
        @Body request: RejectRequest
    ): Response<ResponseBody>

    // 3. Despachar orden (Cambia a COMPLETED y desaparece de la pantalla)
    @PUT("/api/orders/{id}/complete")
    suspend fun completeOrder(@Path("id") orderId: Long): Response<ResponseBody>

    @PUT("/api/orders/{id}/validate-payment")
    suspend fun validatePayment(@Path("id") orderId: Long): Response<ResponseBody>

    // ============================================================================
    // FASE 6A2: Operational Catalog & Configuration
    // ============================================================================

    @GET("/api/v1/menu/items")
    suspend fun getMenuItems(): Response<List<com.restaurant.sushimei.frontend.data.model.CatalogItemDto>>

    @GET("/api/v1/menu/items/{id}")
    suspend fun getMenuItem(@Path("id") id: String): Response<com.restaurant.sushimei.frontend.data.model.CatalogItemDto>

    @GET("/api/v1/menu/items?standaloneOnly=true")
    suspend fun getStandaloneMenuItems(): Response<List<com.restaurant.sushimei.frontend.data.model.CatalogItemDto>>

    @GET("/api/v1/menu/items/{id}/configuration")
    suspend fun getMenuItemConfiguration(@Path("id") id: String): Response<com.restaurant.sushimei.frontend.data.model.ConfigurationResponseDto>

    @POST("/api/v1/menu/items/{id}/quote")
    suspend fun quoteMenuItem(
        @Path("id") id: String,
        @Body request: com.restaurant.sushimei.frontend.data.model.QuoteRequestDto
    ): Response<com.restaurant.sushimei.frontend.data.model.QuoteResponseDto>

    // ============================================================================
    // FASE 6A2: Tags Management
    // ============================================================================

    @GET("/api/v1/menu/tags?includeInactive=false")
    suspend fun getActiveTags(): Response<List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>>

    @GET("/api/v1/menu/tags?includeInactive=true")
    suspend fun getAllTags(): Response<List<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>>

    @POST("/api/v1/menu/tags")
    suspend fun createTag(@Body tag: com.restaurant.sushimei.frontend.data.model.CatalogTagDto): Response<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>

    @PUT("/api/v1/menu/tags/{id}")
    suspend fun updateTag(
        @Path("id") id: String,
        @Body tag: com.restaurant.sushimei.frontend.data.model.CatalogTagDto
    ): Response<com.restaurant.sushimei.frontend.data.model.CatalogTagDto>

    @DELETE("/api/v1/menu/tags/{id}")
    suspend fun deleteTag(@Path("id") id: String): Response<ResponseBody>

    @PUT("/api/v1/menu/items/{id}/tags")
    suspend fun updateItemTags(
        @Path("id") id: String,
        @Body tagIds: List<String>
    ): Response<ResponseBody>
}
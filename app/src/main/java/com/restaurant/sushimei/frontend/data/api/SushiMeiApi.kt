package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.data.model.OrderRecord
import com.restaurant.sushimei.frontend.data.model.RejectRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

}
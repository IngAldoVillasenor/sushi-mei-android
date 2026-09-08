package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.api.ApiException
import com.cardovia.merkon.app.data.api.MerkonApi
import com.cardovia.merkon.app.data.model.ManualPosOrderRequest
import com.cardovia.merkon.app.data.model.ManualPosOrderResponse
import org.json.JSONObject

class RemoteManualPosOrderRepository(
    private val api: MerkonApi
) : IManualPosOrderRepository {
    override suspend fun submitOrder(request: ManualPosOrderRequest): ManualPosOrderResponse {
        val response = api.createOrder(request)

        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        }

        val errorBodyString = try { response.errorBody()?.string() } catch (e: Exception) { null }

        if (!errorBodyString.isNullOrBlank()) {
            try {
                val json = JSONObject(errorBodyString)

                val code = json.optString("code", "UNKNOWN_ERROR")

                val message = json.optString("message", "An unknown error occurred")

                throw ApiException(code, message)
            } catch (e: Exception) {
                if (e is ApiException) throw e

                // Fallback if parsing fails
            }
        }

        throw ApiException("HTTP_ERROR", "Unknown error placing order: HTTP ${response.code()}")
    }

    override suspend fun createOpenSale(request: com.cardovia.merkon.app.data.model.OpenSaleRequest): com.cardovia.merkon.app.data.model.OpenSaleResponse {
        val response = api.createOpenSale(request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response body")
        } else {
            throw Exception("Error : ")
        }
    }
}

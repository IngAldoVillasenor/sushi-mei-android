package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderResponse
import org.json.JSONObject

class RemoteManualPosOrderRepository(
    private val api: SushiMeiApi
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

    override suspend fun createOpenSale(request: com.restaurant.sushimei.frontend.data.model.OpenSaleRequest): com.restaurant.sushimei.frontend.data.model.OpenSaleResponse {
        val response = api.createOpenSale(request)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response body")
        } else {
            throw Exception("Error : ")
        }
    }
}

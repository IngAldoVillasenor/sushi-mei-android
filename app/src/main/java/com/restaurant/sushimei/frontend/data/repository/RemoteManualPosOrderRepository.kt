package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderResponse

class RemoteManualPosOrderRepository(
    private val api: SushiMeiApi
) : IManualPosOrderRepository {
    override suspend fun submitOrder(request: ManualPosOrderRequest): ManualPosOrderResponse {
        val response = api.createOrder(request)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        }
        throw Exception("Unknown error placing order: HTTP ${response.code()}")
    }
}

package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.ManualPosOrderRequest
import com.restaurant.sushimei.frontend.data.model.ManualPosOrderResponse

interface IManualPosOrderRepository {
    suspend fun submitOrder(request: ManualPosOrderRequest): ManualPosOrderResponse
}

package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.model.ManualPosOrderRequest
import com.cardovia.merkon.app.data.model.ManualPosOrderResponse

interface IManualPosOrderRepository {
    suspend fun submitOrder(request: ManualPosOrderRequest): ManualPosOrderResponse
    suspend fun createOpenSale(request: com.cardovia.merkon.app.data.model.OpenSaleRequest): com.cardovia.merkon.app.data.model.OpenSaleResponse
}

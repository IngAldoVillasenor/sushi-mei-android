package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto

class RemoteOperationalOrderRepository(private val api: SushiMeiApi) : IOperationalOrderRepository {
    override suspend fun getOperationalOrderDetail(orderId: Long): OperationalOrderDetailDto {
        val response = api.getOperationalOrderDetail(orderId)
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty body")
        } else {
            throw Exception("Error ${response.code()}: ${response.message()}")
        }
    }
}

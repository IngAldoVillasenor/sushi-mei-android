package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.OperationalOrderDetailDto

interface IOperationalOrderRepository {
    suspend fun getOperationalOrderDetail(orderId: Long): OperationalOrderDetailDto
}

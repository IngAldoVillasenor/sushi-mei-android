package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.CloseBusinessDayRequest
import com.restaurant.sushimei.frontend.data.model.OpenBusinessDayRequest

interface IBusinessDayRepository {
    suspend fun getCurrentBusinessDay(): Result<BusinessDayResponse?>
    suspend fun openBusinessDay(request: OpenBusinessDayRequest): Result<BusinessDayResponse>
    suspend fun closeBusinessDay(request: CloseBusinessDayRequest): Result<BusinessDayResponse>
    suspend fun reopenCurrentBusinessDay(): Result<BusinessDayResponse>
}

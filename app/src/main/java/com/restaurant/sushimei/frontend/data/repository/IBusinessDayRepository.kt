package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.CloseBusinessDayRequest
import com.restaurant.sushimei.frontend.data.model.OpenBusinessDayRequest
import com.restaurant.sushimei.frontend.data.model.CashExpenseRequest
import com.restaurant.sushimei.frontend.data.model.CashExpenseCreateResponse
import com.restaurant.sushimei.frontend.data.model.CashExpenseDto

interface IBusinessDayRepository {
    suspend fun getCurrentBusinessDay(): Result<BusinessDayResponse?>
    suspend fun openBusinessDay(request: OpenBusinessDayRequest): Result<BusinessDayResponse>
    suspend fun closeBusinessDay(request: CloseBusinessDayRequest): Result<BusinessDayResponse>
    suspend fun reopenCurrentBusinessDay(): Result<BusinessDayResponse>
    suspend fun getCashExpenses(): Result<List<CashExpenseDto>>
    suspend fun createCashExpense(request: CashExpenseRequest): Result<CashExpenseCreateResponse>
}

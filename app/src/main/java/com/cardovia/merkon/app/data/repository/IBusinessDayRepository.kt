package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.model.BusinessDayResponse
import com.cardovia.merkon.app.data.model.CloseBusinessDayRequest
import com.cardovia.merkon.app.data.model.OpenBusinessDayRequest
import com.cardovia.merkon.app.data.model.CashExpenseRequest
import com.cardovia.merkon.app.data.model.CashExpenseCreateResponse
import com.cardovia.merkon.app.data.model.CashExpenseDto

interface IBusinessDayRepository {
    suspend fun getCurrentBusinessDay(): Result<BusinessDayResponse?>
    suspend fun openBusinessDay(request: OpenBusinessDayRequest): Result<BusinessDayResponse>
    suspend fun closeBusinessDay(request: CloseBusinessDayRequest): Result<BusinessDayResponse>
    suspend fun reopenCurrentBusinessDay(): Result<BusinessDayResponse>
    suspend fun getCashExpenses(): Result<List<CashExpenseDto>>
    suspend fun createCashExpense(request: CashExpenseRequest): Result<CashExpenseCreateResponse>
}

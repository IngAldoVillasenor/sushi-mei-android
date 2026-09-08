package com.cardovia.merkon.app.data.repository

import com.cardovia.merkon.app.data.api.MerkonApi
import com.cardovia.merkon.app.data.model.BusinessDayResponse
import com.cardovia.merkon.app.data.model.CloseBusinessDayRequest
import com.cardovia.merkon.app.data.model.OpenBusinessDayRequest
import com.cardovia.merkon.app.data.model.CashExpenseRequest
import com.cardovia.merkon.app.data.model.CashExpenseCreateResponse
import com.cardovia.merkon.app.data.model.CashExpenseDto

class RemoteBusinessDayRepository(
    private val api: MerkonApi
) : IBusinessDayRepository {
    override suspend fun getCurrentBusinessDay(): Result<BusinessDayResponse?> {
        return try {
            val response = api.getCurrentBusinessDay()
            if (response.isSuccessful) {
                if (response.code() == 204) {
                    Result.success(null)
                } else {
                    Result.success(response.body())
                }
            } else {
                Result.failure(Exception("HTTP : "))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openBusinessDay(request: OpenBusinessDayRequest): Result<BusinessDayResponse> {
        return try {
            val response = api.openBusinessDay(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP : "))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun closeBusinessDay(request: CloseBusinessDayRequest): Result<BusinessDayResponse> {
        return try {
            val response = api.closeBusinessDay(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP : "))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reopenCurrentBusinessDay(): Result<BusinessDayResponse> {
        return try {
            val response = api.reopenCurrentBusinessDay()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown API Error"
                // Assuming interceptors handle this or we just throw ApiException
                // But wait, the standard interceptor probably handles HTTP codes and throws ApiException.
                // Let's just use the same pattern or throw the parsed exception.
                Result.failure(Exception("HTTP : "))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCashExpenses(): Result<List<CashExpenseDto>> {
        return try {
            val response = api.getCashExpenses()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP : "))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createCashExpense(request: CashExpenseRequest): Result<CashExpenseCreateResponse> {
        return try {
            val response = api.createCashExpense(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP : "))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

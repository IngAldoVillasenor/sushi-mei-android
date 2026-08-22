package com.restaurant.sushimei.frontend.data.repository

import com.restaurant.sushimei.frontend.data.api.SushiMeiApi
import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.CloseBusinessDayRequest
import com.restaurant.sushimei.frontend.data.model.OpenBusinessDayRequest

class RemoteBusinessDayRepository(
    private val api: SushiMeiApi
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
}

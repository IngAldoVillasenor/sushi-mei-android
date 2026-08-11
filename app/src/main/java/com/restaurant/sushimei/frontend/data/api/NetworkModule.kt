package com.restaurant.sushimei.frontend.data.api

import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.restaurant.sushimei.frontend.BuildConfig

object NetworkModule {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        // TODO: In Phase 6S1, inject Auth token here
        // val token = sessionStore.getToken()
        // val request = original.newBuilder().header("Authorization", "Bearer $token").build()
        // chain.proceed(request)
        chain.proceed(original)
    }

    private val errorInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            val bodyString = response.peekBody(Long.MAX_VALUE).string()
            var errorCode = "UNKNOWN_ERROR"
            var errorMessage = "Error HTTP ${response.code}"
            try {
                val apiError = com.google.gson.Gson().fromJson(bodyString, com.restaurant.sushimei.frontend.data.model.ApiErrorDto::class.java)
                if (apiError != null && apiError.code != null) {
                    errorCode = apiError.code
                    errorMessage = apiError.message ?: errorMessage
                }
            } catch (e: Exception) {
                // Ignore parsing error
            }
            
            when {
                errorCode.endsWith("VERSION_CONFLICT") || response.code == 409 -> {
                    throw VersionConflictException(errorMessage)
                }
                errorCode == "ITEM_UNAVAILABLE" -> throw MenuItemUnavailableException(errorMessage)
                errorCode == "CONFIGURATION_CONFLICT" -> throw ConfigurationConflictException(errorMessage)
                else -> throw ApiException(errorCode, errorMessage)
            }
        }
        response
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val sushiMeiApi: SushiMeiApi by lazy {
        retrofit.create(SushiMeiApi::class.java)
    }
}

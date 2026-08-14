package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.BuildConfig
import com.restaurant.sushimei.frontend.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.Instant

object NetworkModule {

    private var authRepository: AuthRepository? = null

    fun initAuthRepository(repository: AuthRepository) {
        authRepository = repository
    }

    private object InstantAdapter : TypeAdapter<Instant>() {
        override fun write(out: JsonWriter, value: Instant?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.toString())
            }
        }

        override fun read(reader: JsonReader): Instant? {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return null
            }
            return Instant.parse(reader.nextString())
        }
    }

    internal val configuredGson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantAdapter)
            .create()
    }

    // ============================================================================
    // COMMON ERROR INTERCEPTOR
    // ============================================================================

    private val errorInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            // Note: 401 is handled by Authenticator, but we can still parse errors here
            val bodyString = response.peekBody(Long.MAX_VALUE).string()
            var errorCode = "UNKNOWN_ERROR"
            var errorMessage = "Error HTTP ${response.code}"
            try {
                val apiError = com.google.gson.Gson().fromJson(
                    bodyString,
                    com.restaurant.sushimei.frontend.data.model.ApiErrorDto::class.java
                )
                errorCode = apiError.code
                errorMessage = apiError.message ?: errorMessage
            } catch (e: Exception) {
                // Ignore parsing error
            }

            when {
                // Ensure we don't blanket 409 as VersionConflict unless it's genuinely VERSION_CONFLICT
                errorCode.endsWith("VERSION_CONFLICT") -> throw VersionConflictException(errorMessage)
                errorCode == "ITEM_UNAVAILABLE" -> throw MenuItemUnavailableException(errorMessage)
                errorCode == "CONFIGURATION_CONFLICT" -> throw ConfigurationConflictException(errorMessage)
                // If it's a 409 but not known, just throw generic ApiException
                response.code == 409 -> throw ApiException(errorCode, errorMessage)
                else -> throw ApiException(errorCode, errorMessage)
            }
        }
        response
    }

    // ============================================================================
    // PUBLIC CLIENT (No Auth)
    // ============================================================================

    private val publicOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // Strict transport safety for refresh endpoint
            .addInterceptor(errorInterceptor)
            .build()
    }

    val publicSushiMeiApi: PublicSushiMeiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(publicOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(configuredGson))
            .build()
            .create(PublicSushiMeiApi::class.java)
    }

    // ============================================================================
    // PROTECTED CLIENT (AuthInterceptor + Authenticator)
    // ============================================================================

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = authRepository?.getAccessToken()
        val requestBuilder = original.newBuilder()
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val tokenAuthenticator = Authenticator { route: Route?, response: Response ->
        // Prevent infinite loops: if the request already failed with 401 twice, give up
        if (response.responseCount >= 2) {
            return@Authenticator null
        }

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        val newToken = runBlocking {
            authRepository?.refreshSession(failedToken)
        }

        if (newToken != null) {
            // Retry the request with the new token
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        } else {
            null // Cannot refresh, give up
        }
    }

    private val protectedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(errorInterceptor)
            .build()
    }

    val sushiMeiApi: SushiMeiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(protectedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(configuredGson))
            .build()
            .create(SushiMeiApi::class.java)
    }
}

// Extension to count retries in OkHttp response
private val Response.responseCount: Int
    get() {
        var result = 1
        var priorResponse = this.priorResponse
        while (priorResponse != null) {
            result++
            priorResponse = priorResponse.priorResponse
        }
        return result
    }

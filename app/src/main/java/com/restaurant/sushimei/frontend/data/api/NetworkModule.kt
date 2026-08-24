package com.restaurant.sushimei.frontend.data.api

import com.restaurant.sushimei.frontend.BuildConfig
import com.restaurant.sushimei.frontend.data.repository.AuthRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

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

    private object LocalDateAdapter : TypeAdapter<LocalDate>() {
        override fun write(out: JsonWriter, value: LocalDate?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.toString())
            }
        }

        override fun read(reader: JsonReader): LocalDate? {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return null
            }
            return LocalDate.parse(reader.nextString())
        }
    }

    internal val configuredGson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantAdapter)
            .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter)
            .create()
    }

    // ============================================================================
    // COMMON ERROR INTERCEPTOR
    // ============================================================================

    private val diagnosticsLogger: DiagnosticsLogger = AndroidDiagnosticsLogger

    private val requestDiagnosticsInterceptor = RequestDiagnosticsInterceptor(
        logger = diagnosticsLogger,
        debugEnabled = BuildConfig.DEBUG
    )

    private val errorInterceptor = ApiErrorInterceptor(configuredGson, diagnosticsLogger)

    // ============================================================================
    // PUBLIC CLIENT (No Auth)
    // ============================================================================

    private val publicOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // Strict transport safety for refresh endpoint
            .addInterceptor(requestDiagnosticsInterceptor)
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
            .addInterceptor(requestDiagnosticsInterceptor)
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

internal class RequestDiagnosticsInterceptor(
    private val logger: DiagnosticsLogger,
    private val debugEnabled: Boolean,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestId = original.header(REQUEST_ID_HEADER) ?: requestIdFactory()
        val request = original.newBuilder().header(REQUEST_ID_HEADER, requestId).build()
        val fields = mapOf(
            "requestId" to requestId,
            "method" to request.method,
            "path" to request.url.encodedPath
        )
        if (debugEnabled) {
            logger.debug("api_request", fields)
        }
        return try {
            chain.proceed(request).also { response ->
                if (debugEnabled) {
                    logger.debug(
                        "api_response",
                        fields + mapOf(
                            "requestId" to (response.header(REQUEST_ID_HEADER) ?: requestId),
                            "status" to response.code
                        )
                    )
                }
            }
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: IOException) {
            logger.error(
                "api_transport_error",
                fields + mapOf("cause" to exception.javaClass.simpleName)
            )
            throw exception
        }
    }

    private companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}

internal class ApiErrorInterceptor(
    private val gson: Gson,
    private val logger: DiagnosticsLogger
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            return response
        }

        val requestId = response.header(REQUEST_ID_HEADER) ?: request.header(REQUEST_ID_HEADER)
        var errorCode = "UNKNOWN_ERROR"
        var errorMessage = "Error HTTP ${response.code}"
        try {
            val apiError = gson.fromJson(
                response.peekBody(MAX_ERROR_BODY_BYTES).string(),
                com.restaurant.sushimei.frontend.data.model.ApiErrorDto::class.java
            )
            if (apiError != null) {
                val parsedCode = apiError.code
                if (parsedCode != null && parsedCode.isNotBlank()) {
                    errorCode = parsedCode
                }
                val parsedMessage = apiError.message
                if (parsedMessage != null && parsedMessage.isNotBlank()) {
                    errorMessage = parsedMessage
                }
            }
        } catch (_: Exception) {
            // The status, endpoint and requestId still make an unparseable response diagnosable.
        }

        logger.error(
            "api_error",
            mapOf(
                "requestId" to requestId,
                "method" to request.method,
                "path" to request.url.encodedPath,
                "status" to response.code,
                "code" to errorCode
            )
        )

        val exception = when {
            errorCode == "BUSINESS_DAY_CLOSED" ->
                BusinessDayClosedException(errorMessage, response.code, requestId)
            errorCode.endsWith("VERSION_CONFLICT") ->
                VersionConflictException(errorMessage, response.code, requestId)
            errorCode == "ITEM_UNAVAILABLE" ->
                MenuItemUnavailableException(errorMessage, response.code, requestId)
            errorCode == "CONFIGURATION_CONFLICT" ->
                ConfigurationConflictException(errorMessage, response.code, requestId)
            else -> ApiException(errorCode, errorMessage, response.code, requestId)
        }
        response.close()
        throw exception
    }

    private companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MAX_ERROR_BODY_BYTES = 64L * 1024L
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

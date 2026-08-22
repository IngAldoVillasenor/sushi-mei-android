package com.restaurant.sushimei.frontend.data.api

import java.io.IOException

open class ApiException(
    val code: String,
    message: String,
    val httpStatus: Int? = null,
    val requestId: String? = null
) : IOException(message) {
    fun referenceSuffix(): String = requestId
        ?.takeIf { it.isNotBlank() }
        ?.let { " (Ref. ${it.take(8)})" }
        .orEmpty()
}

class VersionConflictException(
    message: String = "Conflicto de versión detectado (HTTP 409). El recurso fue modificado por otro usuario.",
    httpStatus: Int? = 409,
    requestId: String? = null
) : ApiException("VERSION_CONFLICT", message, httpStatus, requestId)

class MenuItemUnavailableException(message: String, httpStatus: Int? = null, requestId: String? = null) :
    ApiException("ITEM_UNAVAILABLE", message, httpStatus, requestId)

class ConfigurationConflictException(message: String, httpStatus: Int? = null, requestId: String? = null) :
    ApiException("CONFIGURATION_CONFLICT", message, httpStatus, requestId)

class BusinessDayClosedException(
    message: String = "El da operativo ya est cerrado. No se pueden procesar ms rdenes.",
    httpStatus: Int? = 409,
    requestId: String? = null
) : ApiException("BUSINESS_DAY_CLOSED", message, httpStatus, requestId)

package com.restaurant.sushimei.frontend.data.api

import java.io.IOException

open class ApiException(val code: String, message: String) : IOException(message)

class VersionConflictException(message: String = "Conflicto de versión detectado (HTTP 409). El recurso fue modificado por otro usuario.") : ApiException("VERSION_CONFLICT", message)
class MenuItemUnavailableException(message: String) : ApiException("ITEM_UNAVAILABLE", message)
class ConfigurationConflictException(message: String) : ApiException("CONFIGURATION_CONFLICT", message)

package com.restaurant.sushimei.frontend.data.api

import java.io.IOException

class VersionConflictException(message: String = "Conflicto de versión detectado (HTTP 409). El recurso fue modificado por otro usuario.") : IOException(message)

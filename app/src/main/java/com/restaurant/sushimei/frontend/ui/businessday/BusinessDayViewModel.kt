package com.restaurant.sushimei.frontend.ui.businessday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.CloseBusinessDayRequest
import com.restaurant.sushimei.frontend.data.model.OpenBusinessDayRequest
import com.restaurant.sushimei.frontend.data.repository.IBusinessDayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed class BusinessDayState {
    object Loading : BusinessDayState()
    object NotOpen : BusinessDayState()
    data class Open(val day: BusinessDayResponse) : BusinessDayState()
    data class Closed(val day: BusinessDayResponse) : BusinessDayState()
    data class Error(val message: String) : BusinessDayState()
}

class BusinessDayViewModel(
    private val repository: IBusinessDayRepository,
    private val printManager: com.restaurant.sushimei.frontend.PrintManager,
    private val printJobRepository: com.restaurant.sushimei.frontend.data.repository.IPrintJobRepository
) : ViewModel() {

    private val _state = MutableStateFlow<BusinessDayState>(BusinessDayState.Loading)
    val state: StateFlow<BusinessDayState> = _state.asStateFlow()
    
    private val _printMessage = MutableStateFlow<String?>(null)
    val printMessage: StateFlow<String?> = _printMessage.asStateFlow()
    
    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()
    
    
    private val _reopenMessage = MutableStateFlow<String?>(null)
    val reopenMessage: StateFlow<String?> = _reopenMessage.asStateFlow()
    
    fun clearReopenMessage() {
        _reopenMessage.value = null
    }

    fun reopenBusinessDay() {
        val currentState = _state.value
        if (currentState !is BusinessDayState.Closed) return
        
        viewModelScope.launch {
            _state.value = BusinessDayState.Loading
            val result = repository.reopenCurrentBusinessDay()
            if (result.isSuccess) {
                val day = result.getOrNull()
                if (day != null) {
                    _state.value = BusinessDayState.Open(day)
                    _reopenMessage.value = "Día reabierto correctamente"
                }
            } else {
                val exception = result.exceptionOrNull()
                val message = if (exception is com.restaurant.sushimei.frontend.data.api.ApiException) {
                    when (exception.code) {
                        "BUSINESS_DAY_NOT_CLOSED" -> "El día no está cerrado."
                        "BUSINESS_DAY_REOPEN_NOT_ALLOWED" -> "No está permitido reabrir el día."
                        else -> "Error del servidor al reabrir el día (${exception.code})"
                    }
                } else {
                    exception?.message ?: "Error al reabrir"
                }
                _state.value = currentState
                _reopenMessage.value = message
            }
        }
    }

    fun clearPrintMessage() {
        _printMessage.value = null
    }

    init {
        loadCurrentBusinessDay()
    }

    fun loadCurrentBusinessDay() {
        viewModelScope.launch {
            _state.value = BusinessDayState.Loading
            val result = repository.getCurrentBusinessDay()
            if (result.isSuccess) {
                val day = result.getOrNull()
                if (day == null) {
                    _state.value = BusinessDayState.NotOpen
                } else if (day.status.name == "OPEN") {
                    _state.value = BusinessDayState.Open(day)
                } else {
                    _state.value = BusinessDayState.Closed(day)
                }
            } else {
                _state.value = BusinessDayState.Error(result.exceptionOrNull()?.message ?: "Error al cargar")
            }
        }
    }

    fun openBusinessDay(amount: BigDecimal) {
        if (_state.value is BusinessDayState.Loading) return
        viewModelScope.launch {
            _state.value = BusinessDayState.Loading
            val result = repository.openBusinessDay(OpenBusinessDayRequest(amount))
            if (result.isSuccess) {
                val day = result.getOrNull()
                if (day != null) {
                    _state.value = BusinessDayState.Open(day)
                }
            } else {
                _state.value = BusinessDayState.Error(result.exceptionOrNull()?.message ?: "Error al abrir")
            }
        }
    }

    fun closeBusinessDay(amount: BigDecimal) {
        if (_state.value !is BusinessDayState.Open) return
        viewModelScope.launch {
            _state.value = BusinessDayState.Loading
            val result = repository.closeBusinessDay(CloseBusinessDayRequest(amount))
            if (result.isSuccess) {
                val day = result.getOrNull()
                if (day != null) {
                    _state.value = BusinessDayState.Closed(day)
                }
            } else {
                _state.value = BusinessDayState.Error(result.exceptionOrNull()?.message ?: "Error al cerrar")
            }
        }
    }

            fun printClosingTicket(day: BusinessDayResponse) {
        viewModelScope.launch {
            if (day.closureId == null) {
                _printMessage.value = "No se pudo identificar el cierre para imprimir."
                return@launch
            }
            _isPrinting.value = true
            _printMessage.value = "Agregando a la cola de impresión..."
            try {
                val snapshotPayload = com.restaurant.sushimei.frontend.data.api.NetworkModule.configuredGson.toJson(day)
                val requestId = "close-${day.closureId}"
                val job = printManager.enqueuePrintJob(
                    documentType = com.restaurant.sushimei.frontend.data.model.PrintDocumentType.BUSINESS_DAY_CLOSE,
                    documentId = day.closureId,
                    requestId = requestId,
                    snapshotPayload = snapshotPayload
                )
                
                _printMessage.value = "Cierre agregado a la cola de impresión"

                // Observe the print job's final state
                printJobRepository.observeJobById(job.id).collect { updatedJob ->
                    if (updatedJob == null) return@collect
                    
                    when (updatedJob.status) {
                        com.restaurant.sushimei.frontend.data.model.PrintJobStatus.PRINTED -> {
                            _printMessage.value = "Cierre impreso correctamente"
                            _isPrinting.value = false
                            throw kotlinx.coroutines.CancellationException("Printed")
                        }
                        com.restaurant.sushimei.frontend.data.model.PrintJobStatus.FAILED -> {
                            _printMessage.value = "No se pudo imprimir el cierre: ${updatedJob.lastError ?: "Error desconocido"}"
                            _isPrinting.value = false
                            throw kotlinx.coroutines.CancellationException("Failed")
                        }
                        else -> {
                            // PENDING or INTERRUPTED, keep observing
                        }
                    }
                }
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Done observing
            } catch (e: Exception) {
                _printMessage.value = "Error al imprimir: ${e.message}"
                _isPrinting.value = false
            }
        }
    }
}

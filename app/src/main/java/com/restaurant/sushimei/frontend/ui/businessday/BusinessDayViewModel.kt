package com.restaurant.sushimei.frontend.ui.businessday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.model.BusinessDayResponse
import com.restaurant.sushimei.frontend.data.model.CashExpenseDto
import com.restaurant.sushimei.frontend.data.model.CashExpenseRequest
import java.util.UUID
import com.restaurant.sushimei.frontend.data.model.CloseBusinessDayRequest
import com.restaurant.sushimei.frontend.data.model.OpenBusinessDayRequest
import com.restaurant.sushimei.frontend.data.repository.IBusinessDayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed interface CashExpensesState {
    data object Idle : CashExpensesState
    data object Loading : CashExpensesState
    data class Loaded(val expenses: List<CashExpenseDto>) : CashExpensesState
    data class Error(val message: String) : CashExpensesState
}

data class PendingCashExpenseSubmission(
    val requestId: UUID,
    val businessDayId: Long,
    val canonicalAmount: BigDecimal,
    val canonicalDescription: String,
    val canonicalNote: String?
)

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

    private val _expensesState = MutableStateFlow<CashExpensesState>(CashExpensesState.Idle)
    val expensesState: StateFlow<CashExpensesState> = _expensesState.asStateFlow()

    private val _expenseSubmitError = MutableStateFlow<String?>(null)
    val expenseSubmitError: StateFlow<String?> = _expenseSubmitError.asStateFlow()

    private val _cashExpensesMessage = MutableStateFlow<String?>(null)
    val cashExpensesMessage: StateFlow<String?> = _cashExpensesMessage.asStateFlow()

    private val _isSubmittingExpense = MutableStateFlow(false)
    val isSubmittingExpense: StateFlow<Boolean> = _isSubmittingExpense.asStateFlow()

    private val _expenseSubmittedSuccessfully = MutableStateFlow(false)
    val expenseSubmittedSuccessfully: StateFlow<Boolean> = _expenseSubmittedSuccessfully.asStateFlow()

    private var pendingSubmission: PendingCashExpenseSubmission? = null
    private var currentLoadGeneration = 0

    private val _printMessage = MutableStateFlow<String?>(null)
    val printMessage: StateFlow<String?> = _printMessage.asStateFlow()

    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private val _reopenMessage = MutableStateFlow<String?>(null)
    val reopenMessage: StateFlow<String?> = _reopenMessage.asStateFlow()

    fun clearReopenMessage() {
        _reopenMessage.value = null
    }

    init {
        loadCurrentBusinessDay()
    }

    private fun canonicalizeString(value: String?): String? {
        val trimmed = value?.trim()?.replace(Regex("\\s+"), " ")
        return if (trimmed.isNullOrBlank()) null else trimmed
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
                    pendingSubmission = null
                    _expensesState.value = CashExpensesState.Idle
                    loadCashExpenses(day.businessDayId)
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

    fun clearCashExpensesMessage() {
        _cashExpensesMessage.value = null
    }

    fun loadCashExpenses(dayId: Long) {
        val generation = ++currentLoadGeneration
        viewModelScope.launch {
            if (_expensesState.value !is CashExpensesState.Loaded) {
                _expensesState.value = CashExpensesState.Loading
            }
            val result = repository.getCashExpenses()

            // Check race condition for generation token and businessDayId
            if (generation != currentLoadGeneration) {
                return@launch
            }
            val currentState = _state.value
            if (currentState !is BusinessDayState.Open || currentState.day.businessDayId != dayId) {
                return@launch
            }

            if (result.isSuccess) {
                _expensesState.value = CashExpensesState.Loaded(result.getOrNull() ?: emptyList())
                _cashExpensesMessage.value = null // clear previous transient message if successful
            } else {
                if (_expensesState.value is CashExpensesState.Loaded) {
                    _cashExpensesMessage.value = "Error al actualizar la lista de egresos."
                } else {
                    _expensesState.value = CashExpensesState.Error(result.exceptionOrNull()?.message ?: "Error al cargar")
                }
            }
        }
    }

    fun loadCurrentBusinessDay() {
        viewModelScope.launch {
            _state.value = BusinessDayState.Loading
            val result = repository.getCurrentBusinessDay()
            if (result.isSuccess) {
                val day = result.getOrNull()
                if (day == null) {
                    _state.value = BusinessDayState.NotOpen
                    pendingSubmission = null
                    _expensesState.value = CashExpensesState.Idle
                } else if (day.status.name == "OPEN") {
                    _state.value = BusinessDayState.Open(day)
                    loadCashExpenses(day.businessDayId)
                } else {
                    _state.value = BusinessDayState.Closed(day)
                    pendingSubmission = null
                    _expensesState.value = CashExpensesState.Idle
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
                    pendingSubmission = null
                    _expensesState.value = CashExpensesState.Idle
                    loadCashExpenses(day.businessDayId)
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
                    pendingSubmission = null
                    _expensesState.value = CashExpensesState.Idle
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

    fun clearExpenseMessageState() {
        _expenseSubmitError.value = null
        _expenseSubmittedSuccessfully.value = false
    }

    fun abandonPendingExpenseSubmission() {
        pendingSubmission = null
        clearExpenseMessageState()
    }

    fun validateExpenseSubmission(amount: BigDecimal?, description: String, note: String?): Boolean {
        if (amount == null || amount <= BigDecimal.ZERO || amount.scale() > 2) return false
        val canonicalDesc = canonicalizeString(description)
        if (canonicalDesc == null || canonicalDesc.length > 500) return false
        val canonicalNote = canonicalizeString(note)
        if (canonicalNote != null && canonicalNote.length > 500) return false
        return true
    }

    fun submitCashExpense(amount: BigDecimal, description: String, note: String?) {
        val current = _state.value
        if (current !is BusinessDayState.Open) {
            _expenseSubmitError.value = "Se requiere que el día esté abierto."
            return
        }
        val dayId = current.day.businessDayId

        if (!validateExpenseSubmission(amount, description, note)) {
            _expenseSubmitError.value = "Datos inválidos."
            return
        }
        val canonicalAmount = amount.stripTrailingZeros()
        val canonicalDesc = canonicalizeString(description)!!
        val canonicalNote = canonicalizeString(note)

        if (_isSubmittingExpense.value) return
        _isSubmittingExpense.value = true
        _expenseSubmitError.value = null

        val currentPending = pendingSubmission
        val requestId = if (currentPending != null &&
            currentPending.businessDayId == dayId &&
            currentPending.canonicalAmount == canonicalAmount &&
            currentPending.canonicalDescription == canonicalDesc &&
            currentPending.canonicalNote == canonicalNote) {
            currentPending.requestId
        } else {
            UUID.randomUUID()
        }

        val newPending = PendingCashExpenseSubmission(requestId, dayId, canonicalAmount, canonicalDesc, canonicalNote)
        pendingSubmission = newPending

        viewModelScope.launch {
            try {
                val request = CashExpenseRequest(
                    requestId = newPending.requestId,
                    amount = newPending.canonicalAmount,
                    description = newPending.canonicalDescription,
                    note = newPending.canonicalNote
                )

                val result = repository.createCashExpense(request)

                if (result.isSuccess) {
                    pendingSubmission = null
                    _expenseSubmittedSuccessfully.value = true

                    val createdExpenseResult = result.getOrNull()
                    if (createdExpenseResult != null) {
                        val currentList = (_expensesState.value as? CashExpensesState.Loaded)?.expenses ?: emptyList()
                        if (currentList.none { it.id == createdExpenseResult.expense.id }) {
                            _expensesState.value = CashExpensesState.Loaded(currentList + createdExpenseResult.expense)
                        }
                    }

                    loadCashExpenses(current.day.businessDayId)
                } else {
                    val exception = result.exceptionOrNull()
                    val message = if (exception is com.restaurant.sushimei.frontend.data.api.ApiException) {
                        when (exception.code) {
                            "BUSINESS_DAY_OPEN_REQUIRED" -> {
                                loadCurrentBusinessDay()
                                "Se requiere que el día esté abierto."
                            }
                            "BUSINESS_DAY_CASH_EXPENSE_IDEMPOTENCY_CONFLICT" -> "Conflicto de idempotencia. Revise los datos e intente de nuevo."
                            "BUSINESS_DAY_CASH_EXPENSES_EXCEED_AVAILABLE_CASH" -> "Los egresos superan el efectivo disponible."
                            else -> "Error del servidor: ${exception.code}"
                        }
                    } else {
                        exception?.message ?: "Error al registrar egreso"
                    }
                    _expenseSubmitError.value = message
                }
            } finally {
                _isSubmittingExpense.value = false
            }
        }
    }
}

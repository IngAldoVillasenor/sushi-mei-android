package com.restaurant.sushimei.frontend.ui.dashboard



import android.content.Context

import androidx.lifecycle.ViewModel

import androidx.lifecycle.ViewModelProvider

import androidx.lifecycle.viewModelScope

import com.restaurant.sushimei.frontend.data.local.provideOperationalOrderRepository

import com.restaurant.sushimei.frontend.data.model.HistoricalOrderSummaryDto

import com.restaurant.sushimei.frontend.data.repository.IOperationalOrderRepository

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch

import kotlinx.coroutines.async

import java.math.BigDecimal

import java.time.LocalDate

import java.time.ZoneId

import java.time.ZonedDateTime



enum class DateRangeOption {

    TODAY,

    LAST_7_DAYS,

    LAST_30_DAYS,

    CUSTOM

}



data class DashboardMetrics(

    // Authoritative financial metrics deferred until backend analytics endpoint is available

    val completedSalesTotal: BigDecimal? = null,

    val completedOrderCount: Int? = null,

    val averageCompletedTicket: BigDecimal? = null,

    val voidedOrderCount: Int? = null,

    val salesBySource: List<Pair<String, BigDecimal>>? = null,



    // Authoritative active orders are supported

    val activeOrderCount: Int = 0

)



sealed class DashboardUiState {

    object Loading : DashboardUiState()

    data class Content(

        val metrics: DashboardMetrics,

        val orders: List<HistoricalOrderSummaryDto>,

        val dateRangeOption: DateRangeOption,

        val customStartDate: LocalDate? = null,

        val customEndDate: LocalDate? = null,

        val hasMore: Boolean = false,

        val isRefreshing: Boolean = false,

        val isPaginating: Boolean = false,

        val paginationError: String? = null

    ) : DashboardUiState()

    data class Error(val message: String) : DashboardUiState()

}



class DashboardViewModel(

    private val operationalOrderRepository: IOperationalOrderRepository

) : ViewModel() {



    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()



    private var currentOption = DateRangeOption.TODAY

    private var customStart: LocalDate? = null

    private var customEnd: LocalDate? = null

    private var currentPage = 0



    // Cache for loaded orders across pagination

    private val loadedOrders = mutableListOf<HistoricalOrderSummaryDto>()

    private var hasMorePages = false



    // Cache metrics to ensure history pagination doesn't discard them

    private var currentMetrics = DashboardMetrics()



    init {

        loadData()

    }



    fun setDateRange(option: DateRangeOption, start: LocalDate? = null, end: LocalDate? = null) {

        currentOption = option

        if (option == DateRangeOption.CUSTOM) {

            customStart = start

            customEnd = end

        }

        loadData()

    }



    fun refresh() {

        if (_uiState.value is DashboardUiState.Content) {

            val content = _uiState.value as DashboardUiState.Content

            _uiState.value = content.copy(isRefreshing = true, paginationError = null)

        }

        loadData()

    }



    fun loadMore() {

        if (!hasMorePages || _uiState.value !is DashboardUiState.Content) return

        val content = _uiState.value as DashboardUiState.Content

        if (content.isRefreshing || content.isPaginating) return



        _uiState.value = content.copy(isPaginating = true, paginationError = null)

        currentPage++



        viewModelScope.launch {

            try {

                val (from, to) = getInstantsForRange()

                val pageData = operationalOrderRepository.getHistoricalOrders(

                    from = from,

                    to = to,

                    page = currentPage,

                    size = 100

                )

                loadedOrders.addAll(pageData.content)

                hasMorePages = currentPage < pageData.totalPages - 1



                _uiState.value = DashboardUiState.Content(

                    metrics = currentMetrics,

                    orders = loadedOrders.toList(),

                    dateRangeOption = currentOption,

                    customStartDate = customStart,

                    customEndDate = customEnd,

                    hasMore = hasMorePages,

                    isRefreshing = false,

                    isPaginating = false,

                    paginationError = null

                )

            } catch (e: Exception) {

                // Recoverable pagination error

                currentPage-- // Revert page increment

                _uiState.value = content.copy(

                    isPaginating = false,

                    paginationError = e.message ?: "Error loading more orders"

                )

            }

        }

    }



    private fun loadData() {

        viewModelScope.launch {

            if (_uiState.value !is DashboardUiState.Content || (_uiState.value as DashboardUiState.Content).isRefreshing.not()) {

                _uiState.value = DashboardUiState.Loading

            }



            currentPage = 0

            loadedOrders.clear()



            try {

                val (from, to) = getInstantsForRange()



                // Fetch concurrently

                val (activeOrders, analytics, pageData) = kotlinx.coroutines.coroutineScope {

                    val activeOrdersDeferred = async { operationalOrderRepository.getOperationalActiveOrders() }

                    val analyticsDeferred = async { operationalOrderRepository.getOperationalAnalytics(from!!, to!!) }

                    val pageDataDeferred = async { operationalOrderRepository.getHistoricalOrders(from = from, to = to, page = 0, size = 100) }



                    Triple(activeOrdersDeferred.await(), analyticsDeferred.await(), pageDataDeferred.await())

                }



                // Map the source labels

                val sourceLabels = mapOf(

                    "WHATSAPP_AI" to "WhatsApp",

                    "ANDROID_MANUAL" to "Venta manual",

                    "COUNTER" to "Mostrador",

                    "VENDIS_IMPORT" to "Vendis"

                )



                currentMetrics = DashboardMetrics(

                    completedSalesTotal = analytics.completedRevenue,

                    completedOrderCount = analytics.completedOrderCount.toInt(),

                    averageCompletedTicket = analytics.averageCompletedTicket,

                    voidedOrderCount = analytics.voidedOrderCount.toInt(),

                    salesBySource = analytics.salesBySource.map {

                        val label = if (it.source == null) "Sin origen" else sourceLabels[it.source] ?: it.source

                        label to it.completedRevenue

                    },

                    activeOrderCount = activeOrders.size

                )



                loadedOrders.addAll(pageData.content)

                hasMorePages = 0 < pageData.totalPages - 1



                _uiState.value = DashboardUiState.Content(

                    metrics = currentMetrics,

                    orders = loadedOrders.toList(),

                    dateRangeOption = currentOption,

                    customStartDate = customStart,

                    customEndDate = customEnd,

                    hasMore = hasMorePages,

                    isRefreshing = false,

                    isPaginating = false,

                    paginationError = null

                )



            } catch (e: Exception) {

                // If it's a full refresh and we fail, we show the Error state

                _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error")

            }

        }

    }



    private fun getInstantsForRange(): Pair<String?, String?> {

        val zoneId = ZoneId.of("America/Mexico_City")

        val now = ZonedDateTime.now(zoneId)



        val (start, end) = when (currentOption) {

            DateRangeOption.TODAY -> {

                now.toLocalDate().atStartOfDay(zoneId) to now.toLocalDate().plusDays(1).atStartOfDay(zoneId)

            }

            DateRangeOption.LAST_7_DAYS -> {

                now.toLocalDate().minusDays(6).atStartOfDay(zoneId) to now.toLocalDate().plusDays(1).atStartOfDay(zoneId)

            }

            DateRangeOption.LAST_30_DAYS -> {

                now.toLocalDate().minusDays(29).atStartOfDay(zoneId) to now.toLocalDate().plusDays(1).atStartOfDay(zoneId)

            }

            DateRangeOption.CUSTOM -> {

                if (customStart != null && customEnd != null) {

                    customStart!!.atStartOfDay(zoneId) to customEnd!!.plusDays(1).atStartOfDay(zoneId)

                } else {

                    now.toLocalDate().atStartOfDay(zoneId) to now.toLocalDate().plusDays(1).atStartOfDay(zoneId)

                }

            }

        }



        return start.toInstant().toString() to end.toInstant().toString()

    }



    companion object {

        fun factory(context: Context): ViewModelProvider.Factory =

            object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")

                override fun <T : ViewModel> create(modelClass: Class<T>): T =

                    DashboardViewModel(provideOperationalOrderRepository(context)) as T

            }

    }

}

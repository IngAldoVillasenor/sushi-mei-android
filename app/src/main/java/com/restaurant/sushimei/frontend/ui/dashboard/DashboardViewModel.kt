package com.restaurant.sushimei.frontend.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.restaurant.sushimei.frontend.data.local.provideOrderRepository
import com.restaurant.sushimei.frontend.data.model.Order
import com.restaurant.sushimei.frontend.data.repository.IOrderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Métricas calculadas en tiempo real a partir de las órdenes del día.
 *
 * Cada vez que se despacha una nueva orden, Room emite una actualización
 * y el Dashboard se refresca automáticamente sin ninguna intervención.
 */
data class DashboardMetrics(
    /** Total recaudado hoy (suma de órdenes DISPATCHED). */
    val totalHoy: Double = 0.0,
    /** Número de órdenes despachadas hoy. */
    val ordenesCompletadas: Int = 0,
    /** Número de órdenes activas (PENDING + PREPARING + READY). */
    val ordenesActivas: Int = 0,
    /** Ticket promedio de hoy. 0 si no hay órdenes. */
    val ticketPromedio: Double = 0.0,
    /** Top 5 productos más vendidos hoy (nombre → unidades). */
    val topProductos: List<Pair<String, Int>> = emptyList(),
    /** Órdenes por hora del día [0..23] (completadas hoy). */
    val ordenesPorHora: List<Int> = List(24) { 0 }
)

class DashboardViewModel(
    orderRepository: IOrderRepository
) : ViewModel() {

    val metrics: StateFlow<DashboardMetrics> = combine(
        orderRepository.observeDispatchedToday(),
        orderRepository.activeOrders
    ) { dispatched, active ->
        calcularMetricas(dispatched, active)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardMetrics()
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Cálculo de métricas
    // ─────────────────────────────────────────────────────────────────────────

    private fun calcularMetricas(
        dispatched: List<Order>,
        active: List<Order>
    ): DashboardMetrics {
        val total = dispatched.sumOf { it.total }
        val promedio = if (dispatched.isNotEmpty()) total / dispatched.size else 0.0

        // Conteo de unidades por producto en todas las órdenes despachadas
        val conteoProductos = mutableMapOf<String, Int>()
        for (order in dispatched) {
            for (configuredProduct in order.items) {
                val nombre = configuredProduct.name
                conteoProductos[nombre] = (conteoProductos[nombre] ?: 0) + configuredProduct.quantity
            }
        }
        val topProductos = conteoProductos.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        // Órdenes por hora: index = hora del día (0..23)
        val porHora = MutableList(24) { 0 }
        for (order in dispatched) {
            val hora = java.util.Calendar.getInstance().apply {
                timeInMillis = order.createdAt
            }.get(java.util.Calendar.HOUR_OF_DAY)
            porHora[hora] = porHora[hora] + 1
        }

        return DashboardMetrics(
            totalHoy            = total,
            ordenesCompletadas  = dispatched.size,
            ordenesActivas      = active.size,
            ticketPromedio      = promedio,
            topProductos        = topProductos,
            ordenesPorHora      = porHora
        )
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(provideOrderRepository(context)) as T
            }
    }
}

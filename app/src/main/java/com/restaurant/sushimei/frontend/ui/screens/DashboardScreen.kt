package com.restaurant.sushimei.frontend.ui.screens



import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.tween

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.lifecycle.viewmodel.compose.viewModel

import com.restaurant.sushimei.frontend.ui.dashboard.DashboardMetrics

import com.restaurant.sushimei.frontend.ui.dashboard.DashboardUiState

import com.restaurant.sushimei.frontend.ui.dashboard.DashboardViewModel

import com.restaurant.sushimei.frontend.ui.dashboard.DateRangeOption

import com.restaurant.sushimei.frontend.data.model.HistoricalOrderSummaryDto

import java.time.format.DateTimeFormatter

import java.time.ZoneId



private val ColorPrimary   = Color(0xFF7C4DFF)

private val ColorAccent    = Color(0xFFFF6D00)

private val ColorSuccess   = Color(0xFF00C853)

private val ColorInfo      = Color(0xFF00B0FF)

private val ColorError     = Color(0xFFD32F2F)



@Composable

fun DashboardScreen(

    viewModel: DashboardViewModel = run {

        val context = LocalContext.current

        viewModel(factory = DashboardViewModel.factory(context))

    }

) {

    val uiState by viewModel.uiState.collectAsState()



    when (val state = uiState) {

        is DashboardUiState.Loading -> {

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                CircularProgressIndicator()

            }

        }

        is DashboardUiState.Error -> {

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text("Error al cargar el dashboard", color = MaterialTheme.colorScheme.error)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(state.message)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { viewModel.refresh() }) {

                        Text("Reintentar")

                    }

                }

            }

        }

        is DashboardUiState.Content -> {

            DashboardContent(

                state = state,

                onDateRangeSelected = { viewModel.setDateRange(it) },

                onLoadMore = { viewModel.loadMore() },

                onRefresh = { viewModel.refresh() }

            )

        }

    }

}



@Composable

private fun DashboardContent(

    state: DashboardUiState.Content,

    onDateRangeSelected: (DateRangeOption) -> Unit,

    onLoadMore: () -> Unit,

    onRefresh: () -> Unit

) {

    val metrics = state.metrics



    LazyColumn(

        modifier = Modifier

            .fillMaxSize()

            .background(MaterialTheme.colorScheme.background)

            .padding(horizontal = 24.dp),

        contentPadding = PaddingValues(vertical = 24.dp),

        verticalArrangement = Arrangement.spacedBy(20.dp)

    ) {

        item {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Column {

                    Text(

                        text = "Dashboard",

                        style = MaterialTheme.typography.headlineMedium,

                        fontWeight = FontWeight.Bold

                    )

                    Text(

                        text = "Vista de ventas históricas",

                        style = MaterialTheme.typography.bodyMedium,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    if (metrics.activeOrderCount > 0) {

                        Badge(containerColor = ColorAccent) {

                            Text(

                                text = "${metrics.activeOrderCount} activas",

                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),

                                style = MaterialTheme.typography.labelMedium

                            )

                        }

                    }

                    Button(onClick = onRefresh, enabled = !state.isRefreshing) {

                        Text("Actualizar")

                    }

                }

            }

        }



        item {

            DateRangeSelector(state.dateRangeOption, onDateRangeSelected)

        }



        item {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(16.dp)

            ) {

                KpiCard(

                    modifier = Modifier.weight(1f),

                    label = "Total (Completadas)",

                    value = metrics.completedSalesTotal?.let { "$${formatMoney(it)}" } ?: "--",

                    color = ColorSuccess

                )

                KpiCard(

                    modifier = Modifier.weight(1f),

                    label = "Completadas",

                    value = metrics.completedOrderCount?.toString() ?: "--",

                    color = ColorPrimary

                )

                KpiCard(

                    modifier = Modifier.weight(1f),

                    label = "Ticket promedio",

                    value = metrics.averageCompletedTicket?.let { "$${formatMoney(it)}" } ?: "--",

                    color = ColorInfo

                )

                KpiCard(

                    modifier = Modifier.weight(1f),

                    label = "Anuladas",

                    value = metrics.voidedOrderCount?.toString() ?: "--",

                    color = ColorError

                )

            }

        }



        item {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(16.dp)

            ) {

                SalesBySourceCard(

                    salesBySource = metrics.salesBySource,

                    modifier = Modifier.fillMaxWidth() // Takes full width since we removed ContractGapCard

                )

            }

        }



        item {

            Text(

                "Órdenes Históricas",

                style = MaterialTheme.typography.titleLarge,

                fontWeight = FontWeight.Bold,

                modifier = Modifier.padding(top = 16.dp)

            )

        }



        items(state.orders) { order ->

            HistoricalOrderRow(order)

        }



        if (state.paginationError != null) {

            item {

                Text(

                    text = "Error al cargar más: ${state.paginationError}",

                    color = MaterialTheme.colorScheme.error,

                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),

                    textAlign = androidx.compose.ui.text.style.TextAlign.Center

                )

            }

        }



        if (state.hasMore) {

            item {

                Box(

                    modifier = Modifier.fillMaxWidth().padding(16.dp),

                    contentAlignment = Alignment.Center

                ) {

                    if (state.isPaginating) {

                        CircularProgressIndicator()

                    } else {

                        OutlinedButton(onClick = onLoadMore) {

                            Text("Cargar más")

                        }

                    }

                }

            }

        }

    }

}



@Composable

private fun DateRangeSelector(

    selected: DateRangeOption,

    onSelect: (DateRangeOption) -> Unit

) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(8.dp)

    ) {

        val options = listOf(

            DateRangeOption.TODAY to "Hoy",

            DateRangeOption.LAST_7_DAYS to "Últimos 7 días",

            DateRangeOption.LAST_30_DAYS to "Últimos 30 días"

        )

        options.forEach { (option, label) ->

            FilterChip(

                selected = selected == option,

                onClick = { onSelect(option) },

                label = { Text(label) }

            )

        }

    }

}



@Composable

private fun KpiCard(

    modifier: Modifier,

    label: String,

    value: String,

    color: Color

) {

    Card(

        modifier = modifier,

        shape = RoundedCornerShape(16.dp),

        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),

        elevation = CardDefaults.cardElevation(4.dp)

    ) {

        Column(

            modifier = Modifier.padding(16.dp),

            verticalArrangement = Arrangement.spacedBy(8.dp)

        ) {

            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)

        }

    }

}



@Composable

private fun SalesBySourceCard(

    salesBySource: List<Pair<String, java.math.BigDecimal>>?,

    modifier: Modifier

) {

    Card(

        modifier = modifier,

        shape = RoundedCornerShape(16.dp),

        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),

        elevation = CardDefaults.cardElevation(4.dp)

    ) {

        Column(

            modifier = Modifier.padding(20.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)

        ) {

            Text(

                "Ventas por origen",

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold

            )



            if (salesBySource == null) {

                Text(

                    "Métrica no disponible",

                    style = MaterialTheme.typography.bodyMedium,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            } else if (salesBySource.isEmpty()) {

                Text(

                    "Sin órdenes en el periodo",

                    style = MaterialTheme.typography.bodyMedium,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            } else {

                val maxRevenue = salesBySource.firstOrNull()?.second?.toFloat() ?: 1f

                val safeMax = if (maxRevenue <= 0f) 1f else maxRevenue

                salesBySource.forEach { (source, revenue) ->

                    val ratio = (revenue.toFloat() / safeMax).coerceIn(0f, 1f)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        Row(

                            modifier = Modifier.fillMaxWidth(),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Text(

                                text = source,

                                style = MaterialTheme.typography.bodyMedium,

                                fontWeight = FontWeight.Medium

                            )

                            Text(

                                text = "$${formatMoney(revenue)}",

                                style = MaterialTheme.typography.bodyMedium,

                                fontWeight = FontWeight.Bold,

                                color = ColorPrimary

                            )

                        }

                        LinearProgressIndicator(

                            progress = { ratio },

                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),

                            color = ColorPrimary,

                            trackColor = MaterialTheme.colorScheme.surfaceVariant

                        )

                    }

                }

            }

        }

    }

}



@Composable

private fun HistoricalOrderRow(order: HistoricalOrderSummaryDto) {

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("America/Mexico_City")) }

    val formattedDate = order.createdAt?.let { dateFormatter.format(it) } ?: "Sin fecha"



    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),

        elevation = CardDefaults.cardElevation(2.dp)

    ) {

        Row(

            modifier = Modifier.padding(16.dp).fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text("Orden #${order.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (order.externalOrderId != null) {

                    Text("Ref Ext: ${order.externalOrderId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(formattedDate, style = MaterialTheme.typography.bodyMedium)

                Text("Origen: ${order.orderSource ?: "N/A"}", style = MaterialTheme.typography.bodySmall)

            }

            Column(horizontalAlignment = Alignment.End) {

                Text(

                    text = "$${formatMoney(order.total ?: java.math.BigDecimal.ZERO)}",

                    style = MaterialTheme.typography.titleMedium,

                    fontWeight = FontWeight.Bold,

                    color = if (order.status == "COMPLETED") ColorSuccess else MaterialTheme.colorScheme.onSurface

                )

                Spacer(modifier = Modifier.height(4.dp))

                Badge(containerColor = if (order.status == "COMPLETED") ColorSuccess else if (order.status == "VOIDED") ColorError else ColorPrimary) {

                    Text(order.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))

                }

            }

        }

    }

}



private fun formatMoney(value: java.math.BigDecimal): String =

    String.format(java.util.Locale.US, "%,.2f", value)

package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.restaurant.sushimei.frontend.ui.dashboard.DashboardMetrics
import com.restaurant.sushimei.frontend.ui.dashboard.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Paleta del Dashboard ───────────────────────────────────────────────────
private val ColorPrimary   = Color(0xFF7C4DFF)   // violeta Sushi Mei
private val ColorAccent    = Color(0xFFFF6D00)   // naranja salmón
private val ColorSuccess   = Color(0xFF00C853)   // verde despacho
private val ColorInfo      = Color(0xFF00B0FF)   // azul activas
private val ColorSurface   = Color(0xFF1E1E2E)   // dark surface
private val ColorCard      = Color(0xFF2A2A3E)   // card background

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = run {
        val context = LocalContext.current
        viewModel(factory = DashboardViewModel.factory(context))
    }
) {
    val metrics by viewModel.metrics.collectAsState()
    val today   = remember {
        SimpleDateFormat("EEEE d 'de' MMMM, yyyy", Locale.forLanguageTag("es-MX"))
            .format(Date())
            .replaceFirstChar { it.uppercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
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
                    text = today,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (metrics.ordenesActivas > 0) {
                Badge(containerColor = ColorAccent) {
                    Text(
                        text = "${metrics.ordenesActivas} activas",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // ── KPI Cards — fila ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KpiCard(
                modifier    = Modifier.weight(1f),
                label       = "Total hoy",
                value       = "$${formatMoney(metrics.totalHoy)}",
                emoji       = "💰",
                color       = ColorSuccess
            )
            KpiCard(
                modifier    = Modifier.weight(1f),
                label       = "Completadas",
                value       = "${metrics.ordenesCompletadas}",
                emoji       = "✅",
                color       = ColorPrimary
            )
            KpiCard(
                modifier    = Modifier.weight(1f),
                label       = "Activas ahora",
                value       = "${metrics.ordenesActivas}",
                emoji       = "🔥",
                color       = ColorAccent
            )
            KpiCard(
                modifier    = Modifier.weight(1f),
                label       = "Ticket promedio",
                value       = "$${formatMoney(metrics.ticketPromedio)}",
                emoji       = "🧾",
                color       = ColorInfo
            )
        }

        // ── Gráfica de barras — órdenes por hora ───────────────────────────
        HourlyBarChart(
            ordenesPorHora = metrics.ordenesPorHora,
            modifier       = Modifier.fillMaxWidth()
        )

        // ── Fila inferior: Top productos + Estado actual ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopProductsCard(
                topProductos = metrics.topProductos,
                modifier     = Modifier.weight(1f)
            )
            ResumenEstadoCard(
                completadas = metrics.ordenesCompletadas,
                activas     = metrics.ordenesActivas,
                modifier    = Modifier.weight(0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KPI Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KpiCard(
    modifier: Modifier,
    label: String,
    value: String,
    emoji: String,
    color: Color
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 18.sp)
                }
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text       = value,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = color
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gráfica de barras — órdenes por hora (Canvas)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HourlyBarChart(
    ordenesPorHora: List<Int>,
    modifier: Modifier
) {
    val maxVal = ordenesPorHora.maxOrNull()?.takeIf { it > 0 } ?: 1
    // Animamos la altura de cada barra al llegar
    val animated = ordenesPorHora.map { count ->
        animateFloatAsState(
            targetValue = count.toFloat() / maxVal,
            animationSpec = tween(durationMillis = 700),
            label = "bar"
        ).value
    }

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Órdenes completadas por hora",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Hoy — ${ordenesPorHora.sum()} en total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            val barColor  = ColorPrimary
            val emptyColor = MaterialTheme.colorScheme.surfaceVariant

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                drawHourlyBars(animated, maxVal, barColor, emptyColor)
            }

            Spacer(Modifier.height(6.dp))

            // Etiquetas de hora cada 3h
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0h", "3h", "6h", "9h", "12h", "15h", "18h", "21h").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun DrawScope.drawHourlyBars(
    normalized: List<Float>,
    maxVal: Int,
    barColor: Color,
    emptyColor: Color
) {
    val totalBars  = 24
    val gap        = 4.dp.toPx()
    val barWidth   = (size.width - gap * (totalBars - 1)) / totalBars
    val chartH     = size.height

    normalized.forEachIndexed { i, ratio ->
        val left    = i * (barWidth + gap)
        val barH    = chartH * ratio
        val top     = chartH - barH

        if (ratio > 0f) {
            drawRoundRect(
                brush        = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.5f)),
                    startY = top,
                    endY   = chartH
                ),
                topLeft      = Offset(left, top),
                size         = Size(barWidth, barH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        } else {
            drawRoundRect(
                color        = emptyColor,
                topLeft      = Offset(left, chartH - 4.dp.toPx()),
                size         = Size(barWidth, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Productos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopProductsCard(
    topProductos: List<Pair<String, Int>>,
    modifier: Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "🏆 Top productos hoy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (topProductos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Sin órdenes despachadas aún",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxCount = topProductos.firstOrNull()?.second ?: 1
                val medals   = listOf("🥇", "🥈", "🥉", "4️⃣", "5️⃣")
                topProductos.forEachIndexed { idx, (nombre, cantidad) ->
                    TopProductRow(
                        medal    = medals.getOrElse(idx) { "${idx + 1}" },
                        nombre   = nombre,
                        cantidad = cantidad,
                        ratio    = cantidad.toFloat() / maxCount
                    )
                }
            }
        }
    }
}

@Composable
private fun TopProductRow(
    medal: String,
    nombre: String,
    cantidad: Int,
    ratio: Float
) {
    val animRatio by animateFloatAsState(
        targetValue    = ratio,
        animationSpec  = tween(600),
        label          = "product_bar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(medal, fontSize = 16.sp)
                Text(
                    text     = nombre,
                    style    = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text  = "×$cantidad",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ColorPrimary
            )
        }
        LinearProgressIndicator(
            progress       = { animRatio },
            modifier       = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color          = ColorPrimary,
            trackColor     = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Resumen de estado
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResumenEstadoCard(
    completadas: Int,
    activas: Int,
    modifier: Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Estado actual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            StatusBubble(
                count = activas,
                label = "En proceso",
                color = ColorAccent
            )
            StatusBubble(
                count = completadas,
                label = "Despachadas",
                color = ColorSuccess
            )
        }
    }
}

@Composable
private fun StatusBubble(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "$count",
                fontSize   = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = color
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMoney(value: Double): String =
    String.format("%,.2f", value)

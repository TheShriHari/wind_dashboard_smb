package com.aeromdc.aeromonitor.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeromdc.aeromonitor.data.model.TurbineRecord
import com.aeromdc.aeromonitor.data.model.toTurbineStatus
import com.aeromdc.aeromonitor.data.model.TurbineStatus
import com.aeromdc.aeromonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ----------------------------------------------------------------
            // Top App Bar
            // ----------------------------------------------------------------
            AeroTopBar(
                lastSyncTime = state.lastSyncTime,
                isOnline = state.errorMessage == null,
                onRefresh = { viewModel.refreshAll() },
            )

            // ----------------------------------------------------------------
            // Error Banner
            // ----------------------------------------------------------------
            AnimatedVisibility(visible = state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorContainer.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(16.dp))
                        Text(
                            "Live data degraded — ${state.errorMessage}",
                            color = Error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refreshAll() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Farm Summary header spans full width
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FarmSummaryCard(state = state)
                    }

                    // Section label
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "TURBINE GRID",
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceVariant,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                "${state.turbines.size} UNITS",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }

                    // Loading state
                    if (state.isLoadingTelemetry && state.turbines.isEmpty()) {
                        items(6) { TurbineCardSkeleton() }
                    } else if (state.turbines.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(message = "No turbine data available.\nCheck your connection or scraper credentials.")
                        }
                    } else {
                        items(state.turbines, key = { it.turbine_name ?: it.card_no ?: it.hashCode().toString() }) { turbine ->
                            TurbineCard(turbine = turbine)
                        }
                    }

                    // Active alerts summary
                    if (state.alerts.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ActiveAlertsSummary(
                                alerts = state.alerts,
                                counts = state.alertCounts,
                            )
                        }
                    }

                    // Bottom padding
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
// Top App Bar
// -----------------------------------------------------------------------
@Composable
fun AeroTopBar(lastSyncTime: String?, isOnline: Boolean, onRefresh: () -> Unit) {
    Surface(
        color = SurfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "AeroMonitor v2.4",
                    style = MaterialTheme.typography.titleLarge,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Wind Farm Alpha",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Connection status dot
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (isOnline) ColorOperational else Error),
                    )
                    Text(
                        if (isOnline) "ONLINE" else "DEGRADED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) ColorOperational else Error,
                    )
                }
                if (lastSyncTime != null) {
                    Text("Sync: $lastSyncTime", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = OnSurfaceVariant)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
// Farm Summary Card
// -----------------------------------------------------------------------
@Composable
fun FarmSummaryCard(state: DashboardUiState) {
    val summary = state.farmSummary
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "FARM OVERVIEW",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                KpiPod(
                    label = "TOTAL",
                    value = summary?.total_turbines?.toString() ?: "—",
                    valueColor = Primary,
                    icon = Icons.Outlined.WindPower,
                )
                KpiPod(
                    label = "RUNNING",
                    value = summary?.operational?.toString() ?: "—",
                    valueColor = ColorOperational,
                    icon = Icons.Filled.CheckCircle,
                )
                KpiPod(
                    label = "FAULTED",
                    value = summary?.failed?.toString() ?: "—",
                    valueColor = Error,
                    icon = Icons.Filled.Error,
                )
                KpiPod(
                    label = "SNOOZED",
                    value = state.alertCounts.SNOOZED.toString(),
                    valueColor = Tertiary,
                    icon = Icons.Filled.Snooze,
                )
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TOTAL ENERGY TODAY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text(
                    if (summary != null) "${"%,.0f".format(summary.total_kwh_today)} kWh" else "— kWh",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun KpiPod(label: String, value: String, valueColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = valueColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
            color = valueColor,
            fontFamily = FontFamily.Monospace,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, letterSpacing = 1.sp)
    }
}

// -----------------------------------------------------------------------
// Turbine Card
// -----------------------------------------------------------------------
@Composable
fun TurbineCard(turbine: TurbineRecord) {
    val status = turbine.status.toTurbineStatus()
    val (statusColor, cardBorderAlpha) = when (status) {
        TurbineStatus.OPERATIONAL -> Pair(ColorOperational, 0.15f)
        TurbineStatus.FAILURE -> Pair(ColorFailure, 0.35f)
        TurbineStatus.MAINTENANCE -> Pair(ColorMaintenance, 0.3f)
        TurbineStatus.OFFLINE -> Pair(ColorOffline, 0.3f)
        TurbineStatus.UNKNOWN -> Pair(ColorUnknown, 0.08f)
    }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    GlassCard(
        borderColor = statusColor.copy(alpha = cardBorderAlpha),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: name + status pip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        turbine.turbine_name ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        (turbine.status ?: "UNKNOWN").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            statusColor.copy(
                                alpha = if (status == TurbineStatus.FAILURE || status == TurbineStatus.OFFLINE)
                                    pulseAlpha else 1f
                            )
                        ),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Metrics grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(label = "OUTPUT", value = turbine.kw?.let { "${it.toInt()} kW" } ?: "— kW")
                MetricItem(label = "WIND", value = turbine.wind_speed?.let { "${"%.1f".format(it)} m/s" } ?: "— m/s")
            }

            Spacer(Modifier.height(10.dp))

            // Daily energy progress bar
            val progress = ((turbine.today_kwh ?: 0.0) / 20000.0).coerceIn(0.0, 1.0).toFloat()
            Column {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = statusColor.copy(alpha = 0.7f),
                    trackColor = Color.White.copy(alpha = 0.05f),
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("DAILY TOTAL", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = OnSurfaceVariant)
                    Text(
                        turbine.today_kwh?.let { "${"%.0f".format(it)} kWh" } ?: "—",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = OnSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = OnSurfaceVariant, letterSpacing = 1.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

// -----------------------------------------------------------------------
// Active Alerts Summary (dashboard sidebar equivalent)
// -----------------------------------------------------------------------
@Composable
fun ActiveAlertsSummary(alerts: List<com.aeromdc.aeromonitor.data.model.AlertRecord>, counts: com.aeromdc.aeromonitor.data.model.AlertCounts) {
    GlassCard(
        borderColor = if (counts.OPEN > 0) Error.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(18.dp))
                    Text("ACTIVE ALERTS", style = MaterialTheme.typography.labelMedium, color = OnSurface, letterSpacing = 1.sp)
                }
                Text(
                    "${alerts.size} PRIORITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (counts.OPEN > 0) Error else OnSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AlertCountChip("OPEN", counts.OPEN, Error)
                AlertCountChip("ACK", counts.ACKNOWLEDGED, Secondary)
                AlertCountChip("SNOOZED", counts.SNOOZED, Tertiary)
            }
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Divider(color = Color.White.copy(alpha = 0.06f))
                Spacer(Modifier.height(8.dp))
                alerts.take(3).forEach { alert ->
                    MiniAlertRow(alert)
                }
                if (alerts.size > 3) {
                    Text(
                        "+${alerts.size - 3} more — go to Alerts tab",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AlertCountChip(label: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun MiniAlertRow(alert: com.aeromdc.aeromonitor.data.model.AlertRecord) {
    val color = if (alert.severity == "CRITICAL") Error else ColorMaintenance
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(alert.turbine_name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontFamily = FontFamily.Monospace)
        }
        Text(alert.state, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

// -----------------------------------------------------------------------
// Skeleton loading card
// -----------------------------------------------------------------------
@Composable
fun TurbineCardSkeleton() {
    val shimmerAnim = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmerAnim.animateFloat(
        initialValue = 0.1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = shimmerAlpha)))
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = shimmerAlpha)))
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.width(50.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = shimmerAlpha)))
                Box(modifier = Modifier.width(50.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = shimmerAlpha)))
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = shimmerAlpha)))
        }
    }
}

// -----------------------------------------------------------------------
// Empty state
// -----------------------------------------------------------------------
@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.WindPower, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// -----------------------------------------------------------------------
// Reusable glassmorphism card
// -----------------------------------------------------------------------
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.1f),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.7f))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
    ) {
        content()
    }
}

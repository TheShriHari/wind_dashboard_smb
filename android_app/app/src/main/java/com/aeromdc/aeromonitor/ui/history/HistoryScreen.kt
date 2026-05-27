package com.aeromdc.aeromonitor.ui.history

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeromdc.aeromonitor.data.model.AlertRecord
import com.aeromdc.aeromonitor.ui.dashboard.GlassCard
import com.aeromdc.aeromonitor.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var expandedSeverity by remember { mutableStateOf(false) }
    var expandedState by remember { mutableStateOf(false) }
    var selectedSeverity by remember { mutableStateOf<String?>(null) }
    var selectedState by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        // ---- Header ----
        Surface(color = SurfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Alert History", style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                        Text("Review past turbine anomalies", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.fetchHistory() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = OnSurfaceVariant)
                    }
                }
            }
        }

        // ---- Filter Bar ----
        Surface(color = SurfaceContainerHigh) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Severity filter
                ExposedDropdownMenuBox(
                    expanded = expandedSeverity,
                    onExpandedChange = { expandedSeverity = !expandedSeverity },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = selectedSeverity ?: "All Severity",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedSeverity) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = OnSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = expandedSeverity,
                        onDismissRequest = { expandedSeverity = false },
                        containerColor = SurfaceContainer,
                    ) {
                        listOf(null to "All Severity", "CRITICAL" to "Critical", "WARNING" to "Warning").forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = OnSurface) },
                                onClick = {
                                    selectedSeverity = value
                                    expandedSeverity = false
                                    viewModel.applyFilters(severity = value, state = selectedState)
                                },
                            )
                        }
                    }
                }

                // State filter
                ExposedDropdownMenuBox(
                    expanded = expandedState,
                    onExpandedChange = { expandedState = !expandedState },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = selectedState ?: "All States",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedState) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = OnSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = expandedState,
                        onDismissRequest = { expandedState = false },
                        containerColor = SurfaceContainer,
                    ) {
                        listOf(null to "All States", "OPEN" to "Open", "ACKNOWLEDGED" to "Acknowledged",
                            "SNOOZED" to "Snoozed", "RESOLVED" to "Resolved").forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = OnSurface) },
                                onClick = {
                                    selectedState = value
                                    expandedState = false
                                    viewModel.applyFilters(severity = selectedSeverity, state = value)
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- Stats Pods ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPod("Critical", state.criticalCount.toString(), Error, modifier = Modifier.weight(1f))
            StatPod("Resolved", state.resolvedCount.toString(), ColorOperational, modifier = Modifier.weight(1f))
            StatPod("Snoozed", state.snoozedCount.toString(), Tertiary, modifier = Modifier.weight(1f))
            StatPod("Warnings", state.warningCount.toString(), ColorMaintenance, modifier = Modifier.weight(1f))
        }

        // ---- Error banner ----
        if (state.errorMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().background(ErrorContainer.copy(alpha = 0.15f)).padding(12.dp)) {
                Text(state.errorMessage!!, color = Error, style = MaterialTheme.typography.labelMedium)
            }
        }

        // ---- Alert List ----
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (state.alerts.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No alerts match the current filters.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.alerts, key = { it.id }) { alert ->
                    AlertHistoryRow(alert = alert)
                }
            }
        }

        // ---- Pagination ----
        Surface(color = SurfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Showing ${state.alerts.size} of ${state.totalCount} alerts",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.prevPage() },
                        enabled = state.currentPage > 1,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous", tint = if (state.currentPage > 1) Primary else OnSurfaceVariant)
                    }
                    Text(
                        "Page ${state.currentPage} / ${state.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                    IconButton(
                        onClick = { viewModel.nextPage() },
                        enabled = state.currentPage < state.totalPages,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = if (state.currentPage < state.totalPages) Primary else OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StatPod(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontFamily = FontFamily.Monospace)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = OnSurfaceVariant)
        }
    }
}

@Composable
fun AlertHistoryRow(alert: AlertRecord) {
    val isCritical = alert.severity == "CRITICAL"
    val severityColor = if (isCritical) Error else ColorMaintenance

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Left: colored severity pip + turbine info
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(severityColor).fillMaxHeight())
                Column {
                    Text(
                        alert.turbine_name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    SeverityChip(isCritical)
                    Spacer(Modifier.height(4.dp))
                    Text(formatTs(alert.first_detected), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontFamily = FontFamily.Monospace)
                    if (alert.acknowledged_by != null) {
                        Text("By: ${alert.acknowledged_by}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    if (alert.resolved_at != null) {
                        Text("Resolved: ${formatTs(alert.resolved_at)}", style = MaterialTheme.typography.labelSmall, color = ColorOperational, fontFamily = FontFamily.Monospace)
                    }
                    if (alert.snoozed_until != null) {
                        Text("Snz until: ${formatTs(alert.snoozed_until)}", style = MaterialTheme.typography.labelSmall, color = Tertiary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            // Right: state badge
            StateChip(alert.state)
        }
    }
}

@Composable
fun SeverityChip(isCritical: Boolean) {
    val color = if (isCritical) Error else ColorMaintenance
    val label = if (isCritical) "CRITICAL" else "WARNING"
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = color)
        }
    }
}

@Composable
fun StateChip(state: String) {
    val (color, label) = when (state.uppercase()) {
        "RESOLVED" -> Pair(ColorOperational, "RESOLVED")
        "OPEN" -> Pair(Error, "OPEN")
        "SNOOZED" -> Pair(Tertiary, "SNOOZED")
        "ACKNOWLEDGED" -> Pair(Primary, "ACK'D")
        else -> Pair(OnSurfaceVariant, state)
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontFamily = FontFamily.Monospace)
    }
}

private fun formatTs(ts: String?): String {
    if (ts == null) return "—"
    return try {
        val instant = Instant.parse(ts)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        ts.take(16)
    }
}

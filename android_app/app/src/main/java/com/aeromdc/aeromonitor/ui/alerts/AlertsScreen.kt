package com.aeromdc.aeromonitor.ui.alerts

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aeromdc.aeromonitor.data.model.AlertRecord
import com.aeromdc.aeromonitor.ui.dashboard.GlassCard
import com.aeromdc.aeromonitor.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlertsScreen(viewModel: AlertsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAckDialog by remember { mutableStateOf(false) }
    var operatorName by remember { mutableStateOf("") }

    // Show snackbar for feedback
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Surface,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Surface)) {
            when {
                state.isLoading -> LoadingState()
                state.openAlerts.isEmpty() -> AllClearScreen(
                    totalActive = state.alerts.size,
                    counts = state.counts,
                    onRefresh = { viewModel.fetchAlerts() },
                )
                else -> {
                    val currentAlert = state.openAlerts.getOrNull(state.currentAlertIndex)
                    if (currentAlert != null) {
                        CriticalAlertModal(
                            alert = currentAlert,
                            alertIndex = state.currentAlertIndex,
                            totalAlerts = state.openAlerts.size,
                            counts = state.counts,
                            isActionInProgress = state.isActionInProgress,
                            onAcknowledge = { showAckDialog = true },
                            onSnooze = { duration -> viewModel.snoozeAlert(currentAlert.id, duration) },
                            onNext = { viewModel.advanceToNextAlert() },
                            onRefresh = { viewModel.fetchAlerts() },
                        )
                    }
                }
            }
        }
    }

    // Operator name dialog for acknowledge
    if (showAckDialog) {
        AlertDialog(
            onDismissRequest = { showAckDialog = false },
            containerColor = SurfaceContainer,
            title = { Text("Acknowledge Alert", color = OnSurface) },
            text = {
                Column {
                    Text("Enter your name to acknowledge this incident:", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = operatorName,
                        onValueChange = { operatorName = it },
                        label = { Text("Operator Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = OnSurfaceVariant,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val alert = viewModel.uiState.value.openAlerts.getOrNull(viewModel.uiState.value.currentAlertIndex)
                        if (alert != null) {
                            viewModel.acknowledgeAlert(alert.id, operatorName.ifBlank { "Operator" })
                        }
                        showAckDialog = false
                        operatorName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer, contentColor = OnErrorContainer),
                ) { Text("ACKNOWLEDGE") }
            },
            dismissButton = {
                TextButton(onClick = { showAckDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
            },
        )
    }
}

// -----------------------------------------------------------------------
// Main alert modal — mirrors Alert_Notification_Code.html blocking overlay
// -----------------------------------------------------------------------
@Composable
fun CriticalAlertModal(
    alert: AlertRecord,
    alertIndex: Int,
    totalAlerts: Int,
    counts: com.aeromdc.aeromonitor.data.model.AlertCounts,
    isActionInProgress: Boolean,
    onAcknowledge: () -> Unit,
    onSnooze: (Int) -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
) {
    val isCritical = alert.severity == "CRITICAL"
    val accentColor = if (isCritical) Error else ColorMaintenance
    val accentContainerColor = if (isCritical) ErrorContainer else Color(0xFF431200)

    val pulseAnim = rememberInfiniteTransition(label = "modal_pulse")
    val glowAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Surface).verticalScroll(rememberScrollState()),
    ) {
        // ---- Top glow bar ----
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp)
                .background(accentColor.copy(alpha = glowAlpha)),
        )

        // ---- Header ----
        Surface(color = SurfaceContainer) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("AeroMonitor v2.4", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = Color(0xFF1A3A1A), shape = RoundedCornerShape(4.dp)) {
                            Text("OPEN: ${counts.OPEN}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = ColorOperational)
                        }
                        Surface(color = Color(0xFF1A1A3A), shape = RoundedCornerShape(4.dp)) {
                            Text("ACK: ${counts.ACKNOWLEDGED}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                        Surface(color = Color(0xFF2A2A1A), shape = RoundedCornerShape(4.dp)) {
                            Text("SNOOZED: ${counts.SNOOZED}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Tertiary)
                        }
                    }
                }
                if (totalAlerts > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Alert ${alertIndex + 1} of $totalAlerts",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                }
            }
        }

        // ---- Alert Card ----
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Icon + severity
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.1f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isCritical) Icons.Filled.Error else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Column {
                    Surface(color = accentContainerColor, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            if (isCritical) "SYSTEM CRITICAL" else "WARNING",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ID: ${alert.error_code ?: alert.id.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Title
            Text(
                "${alert.severity} ALERT — ${alert.turbine_name}",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
            )

            // Message
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    alert.message ?: "No additional details available.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
            }

            // Meta cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AlertMetaCard(modifier = Modifier.weight(1f), label = "FIRST DETECTED", value = formatTs(alert.first_detected))
                AlertMetaCard(modifier = Modifier.weight(1f), label = "ALERT STATE", value = alert.state)
            }

            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Acknowledge
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ACKNOWLEDGE INCIDENT", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                    }
                }

                // Snooze buttons row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { duration ->
                        OutlinedButton(
                            onClick = { onSnooze(duration) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            enabled = !isActionInProgress,
                            border = BorderStroke(1.dp, OutlineVariant),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Snooze", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text("${duration}m", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Next alert / Refresh
                if (totalAlerts > 1) {
                    OutlinedButton(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("NEXT ALERT (${alertIndex + 1}/$totalAlerts)", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.NavigateNext, contentDescription = null, tint = Primary)
                    }
                }

                TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh Alerts", color = OnSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun AlertMetaCard(modifier: Modifier = Modifier, label: String, value: String) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontFamily = FontFamily.Monospace)
        }
    }
}

// -----------------------------------------------------------------------
// All Clear screen — shown when no OPEN alerts
// -----------------------------------------------------------------------
@Composable
fun AllClearScreen(totalActive: Int, counts: com.aeromdc.aeromonitor.data.model.AlertCounts, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = ColorOperational,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("All Clear", style = MaterialTheme.typography.headlineMedium, color = ColorOperational)
        Spacer(Modifier.height(8.dp))
        Text(
            if (totalActive == 0) "No active alerts. All turbines operational."
            else "No OPEN alerts. ${counts.ACKNOWLEDGED} acknowledged, ${counts.SNOOZED} snoozed.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Primary)
            Spacer(Modifier.width(8.dp))
            Text("Refresh", color = Primary)
        }
    }
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Primary)
            Text("Loading alerts…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
    }
}

private fun formatTs(ts: String?): String {
    if (ts == null) return "—"
    return try {
        val instant = Instant.parse(ts)
        val formatter = DateTimeFormatter.ofPattern("HH:mm, dd MMM").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        ts.take(16)
    }
}

package com.aeromdc.aeromonitor.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aeromdc.aeromonitor.data.model.AlertCounts
import com.aeromdc.aeromonitor.data.model.AlertRecord
import com.aeromdc.aeromonitor.data.repository.AeroMonitorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlertsUiState(
    val isLoading: Boolean = true,
    val alerts: List<AlertRecord> = emptyList(),
    val openAlerts: List<AlertRecord> = emptyList(),
    val counts: AlertCounts = AlertCounts(),
    val currentAlertIndex: Int = 0,
    val isActionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AeroMonitorRepository(app)
    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    private val REFRESH_INTERVAL_MS = 30_000L

    init {
        fetchAlerts()
        startAutoRefresh()
    }

    fun fetchAlerts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repo.fetchActiveAlerts()
            result.fold(
                onSuccess = { data ->
                    val openAlerts = data.alerts.filter { it.state == "OPEN" }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        alerts = data.alerts,
                        openAlerts = openAlerts,
                        counts = data.counts,
                        currentAlertIndex = 0,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load alerts: ${e.message}",
                    )
                },
            )
        }
    }

    fun acknowledgeAlert(alertId: String, operator: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            val result = repo.acknowledgeAlert(alertId, operator)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isActionInProgress = false,
                        successMessage = "Alert acknowledged",
                    )
                    delay(300)
                    fetchAlerts()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isActionInProgress = false,
                        errorMessage = "Acknowledge failed: ${e.message}",
                    )
                },
            )
        }
    }

    fun snoozeAlert(alertId: String, durationMinutes: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            val result = repo.snoozeAlert(alertId, durationMinutes)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isActionInProgress = false,
                        successMessage = "Alert snoozed for ${durationMinutes}m",
                    )
                    delay(300)
                    fetchAlerts()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isActionInProgress = false,
                        errorMessage = "Snooze failed: ${e.message}",
                    )
                },
            )
        }
    }

    fun advanceToNextAlert() {
        val state = _uiState.value
        val nextIndex = state.currentAlertIndex + 1
        if (nextIndex < state.openAlerts.size) {
            _uiState.value = state.copy(currentAlertIndex = nextIndex)
        } else {
            fetchAlerts()
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                fetchAlerts()
            }
        }
    }
}

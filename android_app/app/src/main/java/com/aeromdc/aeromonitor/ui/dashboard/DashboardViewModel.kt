package com.aeromdc.aeromonitor.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aeromdc.aeromonitor.data.model.FarmSummary
import com.aeromdc.aeromonitor.data.model.TurbineRecord
import com.aeromdc.aeromonitor.data.model.AlertRecord
import com.aeromdc.aeromonitor.data.model.AlertCounts
import com.aeromdc.aeromonitor.data.repository.AeroMonitorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoadingTelemetry: Boolean = true,
    val isLoadingAlerts: Boolean = true,
    val turbines: List<TurbineRecord> = emptyList(),
    val farmSummary: FarmSummary? = null,
    val alerts: List<AlertRecord> = emptyList(),
    val alertCounts: AlertCounts = AlertCounts(),
    val errorMessage: String? = null,
    val lastSyncTime: String? = null,
    val isRefreshing: Boolean = false,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AeroMonitorRepository(app)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val REFRESH_INTERVAL_MS = 60_000L

    init {
        refreshAll()
        startAutoRefresh()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            fetchTelemetry()
            fetchAlerts()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    private suspend fun fetchTelemetry() {
        _uiState.value = _uiState.value.copy(isLoadingTelemetry = true)
        val result = repo.fetchLiveTelemetry()
        result.fold(
            onSuccess = { data ->
                val now = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                _uiState.value = _uiState.value.copy(
                    isLoadingTelemetry = false,
                    turbines = data.turbines,
                    farmSummary = data.farm_summary,
                    errorMessage = null,
                    lastSyncTime = now,
                )
            },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingTelemetry = false,
                    errorMessage = "Telemetry: ${e.message}",
                )
            },
        )
    }

    private suspend fun fetchAlerts() {
        _uiState.value = _uiState.value.copy(isLoadingAlerts = true)
        val result = repo.fetchActiveAlerts()
        result.fold(
            onSuccess = { data ->
                _uiState.value = _uiState.value.copy(
                    isLoadingAlerts = false,
                    alerts = data.alerts,
                    alertCounts = data.counts,
                )
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(isLoadingAlerts = false)
            },
        )
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                refreshAll()
            }
        }
    }
}

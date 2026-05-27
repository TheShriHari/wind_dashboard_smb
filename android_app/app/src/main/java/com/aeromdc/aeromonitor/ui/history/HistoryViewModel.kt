package com.aeromdc.aeromonitor.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aeromdc.aeromonitor.data.model.AlertRecord
import com.aeromdc.aeromonitor.data.repository.AeroMonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val alerts: List<AlertRecord> = emptyList(),
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val perPage: Int = 25,
    val severityFilter: String? = null,
    val stateFilter: String? = null,
    val errorMessage: String? = null,
    // Stats
    val criticalCount: Int = 0,
    val resolvedCount: Int = 0,
    val snoozedCount: Int = 0,
    val warningCount: Int = 0,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AeroMonitorRepository(app)
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        fetchHistory()
    }

    fun fetchHistory(
        page: Int = _uiState.value.currentPage,
        perPage: Int = _uiState.value.perPage,
        severity: String? = _uiState.value.severityFilter,
        state: String? = _uiState.value.stateFilter,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repo.fetchAlertHistory(page, perPage, severity, state)
            result.fold(
                onSuccess = { data ->
                    val criticals = data.alerts.count { it.severity == "CRITICAL" }
                    val resolved = data.alerts.count { it.state == "RESOLVED" }
                    val snoozed = data.alerts.count { it.state == "SNOOZED" }
                    val warnings = data.alerts.count { it.severity == "WARNING" }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        alerts = data.alerts,
                        totalCount = data.total_count,
                        currentPage = data.page,
                        totalPages = data.total_pages,
                        perPage = data.per_page,
                        severityFilter = severity,
                        stateFilter = state,
                        criticalCount = criticals,
                        resolvedCount = resolved,
                        snoozedCount = snoozed,
                        warningCount = warnings,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed: ${e.message}",
                    )
                },
            )
        }
    }

    fun nextPage() {
        val s = _uiState.value
        if (s.currentPage < s.totalPages) {
            fetchHistory(page = s.currentPage + 1)
        }
    }

    fun prevPage() {
        val s = _uiState.value
        if (s.currentPage > 1) {
            fetchHistory(page = s.currentPage - 1)
        }
    }

    fun applyFilters(severity: String?, state: String?) {
        fetchHistory(page = 1, severity = severity, state = state)
    }

    fun setPerPage(perPage: Int) {
        fetchHistory(page = 1, perPage = perPage)
    }
}

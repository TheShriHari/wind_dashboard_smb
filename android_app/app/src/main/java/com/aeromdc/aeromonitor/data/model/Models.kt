package com.aeromdc.aeromonitor.data.model

import java.time.OffsetDateTime

// -----------------------------------------------------------------------
// Telemetry models (from GET /api/telemetry/live)
// -----------------------------------------------------------------------

data class TurbineRecord(
    val card_no: String?,
    val turbine_name: String?,
    val status: String?,
    val today_kwh: Double?,
    val wind_speed: Double?,
    val kw: Double?,
    val turbine_datetime: String?,
    val collected_at: String?,
)

data class FarmSummary(
    val total_turbines: Int,
    val operational: Int,
    val failed: Int,
    val total_kwh_today: Double,
)

data class LiveTelemetryResponse(
    val turbines: List<TurbineRecord>,
    val farm_summary: FarmSummary,
)

// -----------------------------------------------------------------------
// Alert models (from GET /api/alerts/active and /api/alerts/history)
// -----------------------------------------------------------------------

data class AlertRecord(
    val id: String,
    val turbine_name: String,
    val error_code: String?,
    val message: String?,
    val severity: String,          // "CRITICAL" | "WARNING"
    val state: String,             // "OPEN" | "ACKNOWLEDGED" | "SNOOZED" | "RESOLVED"
    val first_detected: String,
    val last_notified: String?,
    val acknowledged_by: String?,
    val snoozed_until: String?,
    val resolved_at: String?,
)

data class AlertCounts(
    val OPEN: Int = 0,
    val ACKNOWLEDGED: Int = 0,
    val SNOOZED: Int = 0,
)

data class ActiveAlertsResponse(
    val alerts: List<AlertRecord>,
    val counts: AlertCounts,
)

data class AlertHistoryResponse(
    val total_count: Int,
    val page: Int,
    val per_page: Int,
    val total_pages: Int,
    val alerts: List<AlertRecord>,
)

// -----------------------------------------------------------------------
// Request bodies
// -----------------------------------------------------------------------

data class AcknowledgeRequest(val operator: String)
data class SnoozeRequest(val duration_minutes: Int)

// -----------------------------------------------------------------------
// Rooktec scraped card (before saving to DB / sending to UI)
// -----------------------------------------------------------------------

data class ScrapedTurbine(
    val cardNo: String,
    val turbineName: String,
    val status: String,
    val todayKwh: Double?,
    val windSpeed: Double?,
    val kw: Double?,
    val turbineDatetime: String?,
    val rawText: String,
    val collectedAt: String,
)

// -----------------------------------------------------------------------
// Status classification
// -----------------------------------------------------------------------

enum class TurbineStatus { OPERATIONAL, FAILURE, MAINTENANCE, OFFLINE, UNKNOWN }

fun String?.toTurbineStatus(): TurbineStatus {
    val s = (this ?: "").uppercase().trim().trimEnd('.')
    return when {
        s == "OPERATIONAL" -> TurbineStatus.OPERATIONAL
        s in setOf("FAILURE", "FAILED", "ERROR") -> TurbineStatus.FAILURE
        s == "MAINTENANCE" -> TurbineStatus.MAINTENANCE
        s in setOf("OFFLINE", "WIND MILL NOT ANSWERING") -> TurbineStatus.OFFLINE
        else -> TurbineStatus.UNKNOWN
    }
}

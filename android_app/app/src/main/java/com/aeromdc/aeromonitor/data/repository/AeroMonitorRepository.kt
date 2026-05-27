package com.aeromdc.aeromonitor.data.repository

import android.app.Application
import com.aeromdc.aeromonitor.data.model.*
import com.aeromdc.aeromonitor.data.network.AeroMonitorApi
import com.aeromdc.aeromonitor.data.network.RooktecScraper
import com.aeromdc.aeromonitor.util.AppPreferences
import kotlinx.coroutines.flow.first
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Single source of truth for telemetry + alert data.
 *
 * Telemetry: scraped directly from Rooktec (no backend needed)
 * Alerts:    fetched/mutated via the FastAPI backend REST API
 */
class AeroMonitorRepository(private val app: Application) {

    private val prefs = AppPreferences(app)

    // --------------------------------------------------------------------------
    // Retrofit API client (lazily rebuilt when backend URL changes)
    // --------------------------------------------------------------------------
    private var _api: AeroMonitorApi? = null
    private var _apiBaseUrl: String? = null

    private suspend fun getApi(): AeroMonitorApi {
        val url = prefs.backendUrl.first()
        // Normalize: ensure trailing slash
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (_api == null || _apiBaseUrl != normalizedUrl) {
            _api = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AeroMonitorApi::class.java)
            _apiBaseUrl = normalizedUrl
        }
        return _api!!
    }

    // --------------------------------------------------------------------------
    // Rooktec scraper (lazily rebuilt when credentials change)
    // --------------------------------------------------------------------------
    private var _scraper: RooktecScraper? = null

    private suspend fun getScraper(): RooktecScraper {
        val baseUrl = prefs.rooktecBaseUrl.first()
        val username = prefs.rooktecUsername.first()
        val password = prefs.rooktecPassword.first()
        // Always create fresh scraper (session state managed internally)
        _scraper = RooktecScraper(baseUrl, username, password)
        return _scraper!!
    }

    // --------------------------------------------------------------------------
    // Live telemetry: Rooktec scrape → convert to TurbineRecord list
    // --------------------------------------------------------------------------
    suspend fun fetchLiveTelemetry(): Result<LiveTelemetryResponse> {
        return try {
            val scraper = getScraper()
            val scrapeResult = scraper.scrape()

            if (scrapeResult.isFailure) {
                // Fallback: try backend API if Rooktec scrape fails
                val api = getApi()
                val resp = api.getLiveTelemetry()
                if (resp.isSuccessful && resp.body() != null) {
                    Result.success(resp.body()!!)
                } else {
                    Result.failure(Exception("Both Rooktec scrape and backend API failed"))
                }
            } else {
                val scraped = scrapeResult.getOrThrow()
                val turbines = scraped.map { s ->
                    TurbineRecord(
                        card_no = s.cardNo,
                        turbine_name = s.turbineName,
                        status = s.status,
                        today_kwh = s.todayKwh,
                        wind_speed = s.windSpeed,
                        kw = s.kw,
                        turbine_datetime = s.turbineDatetime,
                        collected_at = s.collectedAt,
                    )
                }
                val operational = turbines.count { it.status?.uppercase() == "OPERATIONAL" }
                val failed = turbines.size - operational
                val totalKwh = turbines.sumOf { it.today_kwh ?: 0.0 }
                Result.success(
                    LiveTelemetryResponse(
                        turbines = turbines,
                        farm_summary = FarmSummary(
                            total_turbines = turbines.size,
                            operational = operational,
                            failed = failed,
                            total_kwh_today = totalKwh,
                        ),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --------------------------------------------------------------------------
    // Alerts via backend API
    // --------------------------------------------------------------------------
    suspend fun fetchActiveAlerts(): Result<ActiveAlertsResponse> {
        return try {
            val resp = getApi().getActiveAlerts()
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("API error: ${resp.code()} ${resp.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAlert(alertId: String, operator: String): Result<Unit> {
        return try {
            val resp = getApi().acknowledgeAlert(alertId, AcknowledgeRequest(operator))
            if (resp.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("API error: ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun snoozeAlert(alertId: String, durationMinutes: Int): Result<Unit> {
        return try {
            val resp = getApi().snoozeAlert(alertId, SnoozeRequest(durationMinutes))
            if (resp.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("API error: ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAlertHistory(
        page: Int = 1,
        perPage: Int = 25,
        severity: String? = null,
        state: String? = null,
    ): Result<AlertHistoryResponse> {
        return try {
            val resp = getApi().getAlertHistory(page, perPage, severity, state)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("API error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val resp = getApi().health()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

package com.aeromdc.aeromonitor.data.network

import com.aeromdc.aeromonitor.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the AeroMonitor FastAPI backend.
 * Base URL is configured via Settings (default: http://10.0.2.2/api for emulator).
 */
interface AeroMonitorApi {

    @GET("telemetry/live")
    suspend fun getLiveTelemetry(): Response<LiveTelemetryResponse>

    @GET("alerts/active")
    suspend fun getActiveAlerts(): Response<ActiveAlertsResponse>

    @POST("alerts/{alertId}/acknowledge")
    suspend fun acknowledgeAlert(
        @Path("alertId") alertId: String,
        @Body body: AcknowledgeRequest,
    ): Response<Any>

    @POST("alerts/{alertId}/snooze")
    suspend fun snoozeAlert(
        @Path("alertId") alertId: String,
        @Body body: SnoozeRequest,
    ): Response<Any>

    @GET("alerts/history")
    suspend fun getAlertHistory(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 25,
        @Query("severity") severity: String? = null,
        @Query("state") state: String? = null,
    ): Response<AlertHistoryResponse>

    @GET("../health")
    suspend fun health(): Response<Map<String, String>>
}

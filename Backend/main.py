"""
AeroMonitor v2.4 — FastAPI REST API

Endpoints:
  GET  /api/telemetry/live                — Latest telemetry per turbine
  GET  /api/alerts/active                 — Active alerts (OPEN/ACK/SNOOZED)
  POST /api/alerts/{id}/acknowledge       — Acknowledge an alert
  POST /api/alerts/{id}/snooze           — Snooze an alert (15/30/60 min)
  GET  /api/alerts/history               — Paginated alert history with filters

All DB calls are async — no blocking I/O.
CORS allows all origins in dev (set CORS_ALLOWED_ORIGINS env var for prod).
OpenAPI docs at /docs.
"""

from __future__ import annotations

import os
import uuid
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Optional

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator
from sqlalchemy import func, text
from sqlmodel import and_, or_, select
from sqlmodel.ext.asyncio.session import AsyncSession

from Backend.database import create_db_and_tables, get_session
from Backend.ingestion.collector import create_scheduler
from Backend.models import TurbineAlert, TurbineTelemetry

# ---------------------------------------------------------------------------
# Lifespan: startup / shutdown
# ---------------------------------------------------------------------------

_scheduler = None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    global _scheduler
    # Ensure tables exist (idempotent — Alembic handles actual migrations)
    await create_db_and_tables()
    # Start the 15-minute collection scheduler
    _scheduler = create_scheduler()
    _scheduler.start()
    yield
    # Shutdown
    if _scheduler and _scheduler.running:
        _scheduler.shutdown(wait=False)


# ---------------------------------------------------------------------------
# App definition
# ---------------------------------------------------------------------------

app = FastAPI(
    title="AeroMonitor v2.4 API",
    description=(
        "REST API powering the AeroMonitor wind turbine monitoring dashboard. "
        "Provides live telemetry, alert lifecycle management, and paginated history."
    ),
    version="2.4.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS — allow all origins in dev; restrict via env var in prod
_cors_origins_raw = os.environ.get("CORS_ALLOWED_ORIGINS", "*")
_cors_origins = (
    ["*"]
    if _cors_origins_raw.strip() == "*"
    else [o.strip() for o in _cors_origins_raw.split(",")]
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials=_cors_origins_raw.strip() != "*",
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Pydantic response / request models
# ---------------------------------------------------------------------------


class TurbineRecord(BaseModel):
    card_no: Optional[str]
    turbine_name: Optional[str]
    status: Optional[str]
    today_kwh: Optional[float]
    wind_speed: Optional[float]
    kw: Optional[float]
    turbine_datetime: Optional[datetime]
    collected_at: Optional[datetime]

    class Config:
        from_attributes = True


class FarmSummary(BaseModel):
    total_turbines: int
    operational: int
    failed: int
    total_kwh_today: float


class LiveTelemetryResponse(BaseModel):
    turbines: list[TurbineRecord]
    farm_summary: FarmSummary


class AlertRecord(BaseModel):
    id: uuid.UUID
    turbine_name: str
    error_code: Optional[str]
    message: Optional[str]
    severity: str
    state: str
    first_detected: datetime
    last_notified: Optional[datetime]
    acknowledged_by: Optional[str]
    snoozed_until: Optional[datetime]
    resolved_at: Optional[datetime]

    class Config:
        from_attributes = True


class AlertCounts(BaseModel):
    OPEN: int
    ACKNOWLEDGED: int
    SNOOZED: int


class ActiveAlertsResponse(BaseModel):
    alerts: list[AlertRecord]
    counts: AlertCounts


class AcknowledgeRequest(BaseModel):
    operator: str


class AcknowledgeResponse(BaseModel):
    success: bool
    alert: AlertRecord


class SnoozeRequest(BaseModel):
    duration_minutes: int

    @field_validator("duration_minutes")
    @classmethod
    def validate_duration(cls, v: int) -> int:
        if v not in (15, 30, 60):
            raise ValueError("duration_minutes must be 15, 30, or 60")
        return v


class SnoozeResponse(BaseModel):
    success: bool
    snoozed_until: datetime


class AlertHistoryResponse(BaseModel):
    total_count: int
    page: int
    per_page: int
    total_pages: int
    alerts: list[AlertRecord]


# ---------------------------------------------------------------------------
# Helper: convert ORM object to Pydantic
# ---------------------------------------------------------------------------


def _to_decimal_float(val: Optional[Decimal]) -> Optional[float]:
    return float(val) if val is not None else None


def _alert_to_record(alert: TurbineAlert) -> AlertRecord:
    return AlertRecord(
        id=alert.id,
        turbine_name=alert.turbine_name,
        error_code=alert.error_code,
        message=alert.message,
        severity=alert.severity,
        state=alert.state,
        first_detected=alert.first_detected,
        last_notified=alert.last_notified,
        acknowledged_by=alert.acknowledged_by,
        snoozed_until=alert.snoozed_until,
        resolved_at=alert.resolved_at,
    )


# ---------------------------------------------------------------------------
# GET /api/telemetry/live
# ---------------------------------------------------------------------------


@app.get("/api/telemetry/live", response_model=LiveTelemetryResponse, tags=["Telemetry"])
async def get_live_telemetry(session: AsyncSession = Depends(get_session)) -> LiveTelemetryResponse:
    """
    Returns the single latest telemetry record per turbine using
    PostgreSQL's DISTINCT ON for efficient single-pass retrieval.
    Also returns aggregated farm_summary statistics.
    """
    # DISTINCT ON requires raw SQL since SQLModel doesn't expose it directly
    raw_sql = text(
        """
        SELECT DISTINCT ON (turbine_name)
            id, collected_at, card_no, turbine_name, status,
            today_kwh, wind_speed, kw, turbine_datetime, raw_text
        FROM turbine_telemetry
        WHERE turbine_name IS NOT NULL
        ORDER BY turbine_name, collected_at DESC
        """
    )
    result = await session.exec(raw_sql)  # type: ignore[arg-type]
    rows = result.mappings().all()

    turbines: list[TurbineRecord] = []
    operational_count = 0
    failed_count = 0
    total_kwh = 0.0

    for row in rows:
        status = (row["status"] or "UNKNOWN").upper().strip().rstrip(".")
        kwh_val = float(row["today_kwh"]) if row["today_kwh"] is not None else 0.0

        if status == "OPERATIONAL":
            operational_count += 1
        else:
            failed_count += 1

        total_kwh += kwh_val

        turbines.append(
            TurbineRecord(
                card_no=row["card_no"],
                turbine_name=row["turbine_name"],
                status=row["status"],
                today_kwh=float(row["today_kwh"]) if row["today_kwh"] is not None else None,
                wind_speed=float(row["wind_speed"]) if row["wind_speed"] is not None else None,
                kw=float(row["kw"]) if row["kw"] is not None else None,
                turbine_datetime=row["turbine_datetime"],
                collected_at=row["collected_at"],
            )
        )

    farm_summary = FarmSummary(
        total_turbines=len(turbines),
        operational=operational_count,
        failed=failed_count,
        total_kwh_today=round(total_kwh, 2),
    )

    return LiveTelemetryResponse(turbines=turbines, farm_summary=farm_summary)


# ---------------------------------------------------------------------------
# GET /api/alerts/active
# ---------------------------------------------------------------------------


@app.get("/api/alerts/active", response_model=ActiveAlertsResponse, tags=["Alerts"])
async def get_active_alerts(session: AsyncSession = Depends(get_session)) -> ActiveAlertsResponse:
    """
    Returns all alerts in OPEN, ACKNOWLEDGED, or SNOOZED state.
    Sorted: CRITICAL first, then by first_detected descending.
    """
    result = await session.exec(
        select(TurbineAlert)
        .where(TurbineAlert.state.in_(["OPEN", "ACKNOWLEDGED", "SNOOZED"]))  # type: ignore[attr-defined]
        .order_by(
            # Push non-snoozed/non-acknowledged (OPEN) alerts to the top
            text("CASE WHEN state = 'OPEN' THEN 0 WHEN state = 'ACKNOWLEDGED' THEN 1 ELSE 2 END"),
            # Then CRITICAL severity first
            text("CASE WHEN severity = 'CRITICAL' THEN 0 ELSE 1 END"),
            TurbineAlert.first_detected.desc(),  # type: ignore[attr-defined]
        )
    )
    alerts = result.all()

    counts = AlertCounts(
        OPEN=sum(1 for a in alerts if a.state == "OPEN"),
        ACKNOWLEDGED=sum(1 for a in alerts if a.state == "ACKNOWLEDGED"),
        SNOOZED=sum(1 for a in alerts if a.state == "SNOOZED"),
    )

    return ActiveAlertsResponse(
        alerts=[_alert_to_record(a) for a in alerts],
        counts=counts,
    )


# ---------------------------------------------------------------------------
# POST /api/alerts/{id}/acknowledge
# ---------------------------------------------------------------------------


@app.post(
    "/api/alerts/{alert_id}/acknowledge",
    response_model=AcknowledgeResponse,
    tags=["Alerts"],
)
async def acknowledge_alert(
    alert_id: uuid.UUID,
    body: AcknowledgeRequest,
    session: AsyncSession = Depends(get_session),
) -> AcknowledgeResponse:
    """
    Acknowledge an active alert. Sets state to ACKNOWLEDGED and records the operator name.

    - **404** if the alert does not exist
    - **409** if the alert is already RESOLVED
    """
    alert = await session.get(TurbineAlert, alert_id)

    if alert is None:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found.")

    if alert.state == "RESOLVED":
        raise HTTPException(
            status_code=409,
            detail=f"Alert {alert_id} is already RESOLVED and cannot be acknowledged.",
        )

    alert.state = "ACKNOWLEDGED"
    alert.acknowledged_by = body.operator
    alert.last_notified = datetime.now(timezone.utc)
    session.add(alert)
    await session.commit()
    await session.refresh(alert)

    return AcknowledgeResponse(success=True, alert=_alert_to_record(alert))


# ---------------------------------------------------------------------------
# POST /api/alerts/{id}/snooze
# ---------------------------------------------------------------------------


@app.post(
    "/api/alerts/{alert_id}/snooze",
    response_model=SnoozeResponse,
    tags=["Alerts"],
)
async def snooze_alert(
    alert_id: uuid.UUID,
    body: SnoozeRequest,
    session: AsyncSession = Depends(get_session),
) -> SnoozeResponse:
    """
    Snooze an active alert for 15, 30, or 60 minutes.

    - **400** if duration_minutes is not 15, 30, or 60
    - **409** if the alert is already RESOLVED
    """
    alert = await session.get(TurbineAlert, alert_id)

    if alert is None:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found.")

    if alert.state == "RESOLVED":
        raise HTTPException(
            status_code=409,
            detail=f"Alert {alert_id} is already RESOLVED and cannot be snoozed.",
        )

    snoozed_until = datetime.now(timezone.utc) + timedelta(minutes=body.duration_minutes)
    alert.state = "SNOOZED"
    alert.snoozed_until = snoozed_until
    session.add(alert)
    await session.commit()
    await session.refresh(alert)

    return SnoozeResponse(success=True, snoozed_until=snoozed_until)


# ---------------------------------------------------------------------------
# GET /api/alerts/history
# ---------------------------------------------------------------------------


@app.get("/api/alerts/history", response_model=AlertHistoryResponse, tags=["Alerts"])
async def get_alert_history(
    page: int = Query(default=1, ge=1, description="Page number"),
    per_page: int = Query(default=25, ge=1, le=100, description="Records per page"),
    severity: Optional[str] = Query(default=None, description="Filter: CRITICAL | WARNING"),
    state: Optional[str] = Query(
        default=None, description="Filter: OPEN | ACKNOWLEDGED | SNOOZED | RESOLVED"
    ),
    session: AsyncSession = Depends(get_session),
) -> AlertHistoryResponse:
    """
    Paginated alert history with optional severity and state filters.
    Used by History_Log_Code.html.
    """
    query = select(TurbineAlert)
    count_query = select(func.count()).select_from(TurbineAlert)

    filters = []
    if severity:
        filters.append(TurbineAlert.severity == severity.upper())
    if state:
        filters.append(TurbineAlert.state == state.upper())

    if filters:
        where_clause = and_(*filters)
        query = query.where(where_clause)
        count_query = count_query.where(where_clause)

    # Total count for pagination metadata
    count_result = await session.exec(count_query)  # type: ignore[arg-type]
    total_count = count_result.one()

    # Paginated records — most recent first
    offset = (page - 1) * per_page
    query = (
        query.order_by(TurbineAlert.first_detected.desc())  # type: ignore[attr-defined]
        .offset(offset)
        .limit(per_page)
    )
    result = await session.exec(query)
    alerts = result.all()

    total_pages = max(1, (total_count + per_page - 1) // per_page)

    return AlertHistoryResponse(
        total_count=total_count,
        page=page,
        per_page=per_page,
        total_pages=total_pages,
        alerts=[_alert_to_record(a) for a in alerts],
    )


# ---------------------------------------------------------------------------
# Health check (used by Docker / nginx)
# ---------------------------------------------------------------------------


@app.get("/health", tags=["Infrastructure"])
async def health() -> dict[str, str]:
    """Simple health check endpoint."""
    return {"status": "ok"}

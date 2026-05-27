"""
SQLModel table definitions for AeroMonitor v2.4.

Tables:
  - TurbineTelemetry: append-only time-series telemetry per turbine per collection cycle
  - TurbineAlert: alert lifecycle records with state machine transitions
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from decimal import Decimal
from typing import Optional

from sqlalchemy import DateTime
from sqlmodel import Field, SQLModel


class TurbineTelemetry(SQLModel, table=True):
    """
    One row per turbine per 15-minute collection cycle.
    Indexed on turbine_name and collected_at for efficient live-data queries.
    """

    __tablename__ = "turbine_telemetry"

    id: Optional[int] = Field(default=None, primary_key=True)
    collected_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        sa_type=DateTime(timezone=True),  # type: ignore
        nullable=False,
    )
    card_no: Optional[str] = Field(default=None, max_length=10)
    turbine_name: Optional[str] = Field(default=None, max_length=100, index=True)
    # Status values: OPERATIONAL | FAILURE | MAINTENANCE | OFFLINE | UNKNOWN
    status: Optional[str] = Field(default=None, max_length=50)
    today_kwh: Optional[Decimal] = Field(default=None, decimal_places=2, max_digits=14)
    wind_speed: Optional[Decimal] = Field(default=None, decimal_places=2, max_digits=8)
    kw: Optional[Decimal] = Field(default=None, decimal_places=2, max_digits=12)
    # Timestamp from the turbine's own internal clock
    turbine_datetime: Optional[datetime] = Field(default=None)
    raw_text: Optional[str] = Field(default=None)


class TurbineAlert(SQLModel, table=True):
    """
    Alert lifecycle record. One active alert per turbine at a time.
    State machine: OPEN → ACKNOWLEDGED / SNOOZED → RESOLVED
    Snoozed alerts auto-reopen when snoozed_until expires.
    """

    __tablename__ = "turbine_alerts"

    id: uuid.UUID = Field(
        default_factory=uuid.uuid4,
        primary_key=True,
        nullable=False,
    )
    turbine_name: str = Field(max_length=100, nullable=False, index=True)
    # Format: WT{N}-ERR-{CODE} e.g. WT18-ERR-A04
    error_code: Optional[str] = Field(default=None, max_length=30)
    message: Optional[str] = Field(default=None)
    # CRITICAL | WARNING
    severity: str = Field(max_length=20, nullable=False)
    # OPEN | ACKNOWLEDGED | SNOOZED | RESOLVED
    state: str = Field(default="OPEN", max_length=20, nullable=False, index=True)
    first_detected: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        sa_type=DateTime(timezone=True),  # type: ignore
        nullable=False,
    )
    last_notified: Optional[datetime] = Field(default=None, sa_type=DateTime(timezone=True))
    acknowledged_by: Optional[str] = Field(default=None, max_length=100)
    snoozed_until: Optional[datetime] = Field(default=None, sa_type=DateTime(timezone=True))
    resolved_at: Optional[datetime] = Field(default=None, sa_type=DateTime(timezone=True))

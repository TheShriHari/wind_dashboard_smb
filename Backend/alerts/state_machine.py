"""
Alert state machine for AeroMonitor v2.4.

Runs after every ingestion cycle. Processes each new telemetry record and
manages the turbine_alerts table lifecycle.

State transitions:
  FAILURE/ERROR status  → Create OPEN CRITICAL alert (if no active alert)
  MAINTENANCE/OFFLINE   → Create OPEN WARNING alert (if no active alert)
  SNOOZED + expired     → Re-open to OPEN (clear snoozed_until)
  OPERATIONAL           → Resolve any active alert

Idempotency: Safe to run multiple times on the same records — existing
active alerts are never duplicated.
"""

from __future__ import annotations

import logging
import uuid
from datetime import datetime, timezone

from sqlalchemy import and_, or_
from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from Backend.models import TurbineAlert, TurbineTelemetry

logger = logging.getLogger("aeromdc.alerts")

# Statuses that indicate a turbine is in a failure condition
_CRITICAL_STATUSES = {"FAILURE", "FAILED", "ERROR"}

# Raw text patterns that signal dropout even if status field is ambiguous
_DROPOUT_PATTERNS = {"not running", "disconnected", "fault", "trip"}

# Statuses that produce a WARNING-severity alert
_WARNING_STATUSES = {"MAINTENANCE", "OFFLINE", "WIND MILL NOT ANSWERING"}

# States considered "active" (not yet resolved)
_ACTIVE_STATES = ("OPEN", "ACKNOWLEDGED", "SNOOZED")


def _error_code(turbine_name: str, counter: int = 1) -> str:
    """Generate a standardised error code from turbine name."""
    # Extract numeric part from name like "WT-01", "WT01", "WT-18"
    digits = "".join(filter(str.isdigit, turbine_name.split("-")[-1] if "-" in turbine_name else turbine_name))
    suffix = digits.zfill(2) if digits else "00"
    code_char = chr(ord("A") + (counter - 1) % 26)
    return f"WT{suffix}-ERR-{code_char}{counter:02d}"


def _severity_for_status(status: str, raw_text: str) -> str | None:
    """
    Return 'CRITICAL', 'WARNING', or None based on turbine status.
    None means the turbine is healthy (no alert needed).
    """
    upper_status = status.upper().strip().rstrip(".") if status else ""
    lower_raw = (raw_text or "").lower()

    # 1. Explicit status-based severities take precedence
    if upper_status in _CRITICAL_STATUSES:
        return "CRITICAL"

    if upper_status in _WARNING_STATUSES:
        return "WARNING"

    # 2. Fallback raw text keyword matching for dropouts (excluding "offline" handled as WARNING above)
    if any(pattern in lower_raw for pattern in _DROPOUT_PATTERNS):
        return "CRITICAL"

    return None


def _alert_message(turbine_name: str, status: str, raw_text: str) -> str:
    """Produce a human-readable alert message from available telemetry data."""
    upper_status = status.upper().strip().rstrip(".")
    if upper_status in {"FAILURE", "FAILED"}:
        return (
            f"Turbine {turbine_name} has entered a FAILURE state. "
            "Automated safety systems may have engaged. Immediate inspection required."
        )
    if upper_status == "ERROR":
        return (
            f"Turbine {turbine_name} reported an ERROR condition. "
            "Review diagnostic logs for root cause."
        )
    if upper_status == "MAINTENANCE":
        return (
            f"Turbine {turbine_name} is currently in MAINTENANCE mode. "
            "Power generation suspended."
        )
    if upper_status in {"OFFLINE", "WIND MILL NOT ANSWERING"}:
        return (
            f"Turbine {turbine_name} is not answering. "
            "Telemetry signal lost. Check network connectivity."
        )
    # Fallback: extract useful snippet from raw_text
    excerpt = raw_text[:120].strip() if raw_text else "No additional details available."
    return f"Turbine {turbine_name} status: {status}. Details: {excerpt}"


async def _get_active_alert(
    session: AsyncSession, turbine_name: str
) -> TurbineAlert | None:
    """
    Return the most recent active alert for the given turbine, or None.
    'Active' means state is OPEN, ACKNOWLEDGED, or SNOOZED.
    """
    result = await session.exec(
        select(TurbineAlert)
        .where(
            and_(
                TurbineAlert.turbine_name == turbine_name,
                TurbineAlert.state.in_(_ACTIVE_STATES),  # type: ignore[attr-defined]
            )
        )
        .order_by(TurbineAlert.first_detected.desc())  # type: ignore[attr-defined]
        .limit(1)
    )
    return result.first()


async def run_alert_state_machine(
    session: AsyncSession,
    records: list[TurbineTelemetry],
) -> None:
    """
    Process a list of newly written telemetry records and manage alert lifecycle.

    This function is idempotent: running it twice on identical records
    produces the same final state in turbine_alerts.

    Args:
        session: An open async SQLAlchemy session. Caller is responsible for commit.
        records: Freshly inserted TurbineTelemetry ORM objects from one collection cycle.
    """
    now = datetime.now(timezone.utc)

    for record in records:
        turbine_name = record.turbine_name or "Unknown"
        try:
            async with session.begin_nested():
                if not record.turbine_name:
                    continue

                status = (record.status or "UNKNOWN").upper().strip().rstrip(".")
                raw_text = record.raw_text or ""

                required_severity = _severity_for_status(status, raw_text)

                # ---------------------------------------------------------------
                # Fetch any existing active alert for this turbine
                # ---------------------------------------------------------------
                active_alert = await _get_active_alert(session, record.turbine_name)

                # ---------------------------------------------------------------
                # Case 1: Turbine is operational — resolve any active alert
                # ---------------------------------------------------------------
                if status == "OPERATIONAL" and required_severity is None:
                    if active_alert is not None:
                        active_alert.state = "RESOLVED"
                        active_alert.resolved_at = now
                        session.add(active_alert)
                        logger.info(
                            "Resolved alert %s for turbine %s (status: OPERATIONAL).",
                            active_alert.id,
                            record.turbine_name,
                        )
                    continue

                # ---------------------------------------------------------------
                # Case 2: UNKNOWN status — preserve existing state, no new alerts
                # ---------------------------------------------------------------
                if status == "UNKNOWN":
                    logger.debug("Turbine %s is UNKNOWN — preserving alert state.", record.turbine_name)
                    continue

                # ---------------------------------------------------------------
                # Case 3: Turbine needs an alert (CRITICAL or WARNING)
                # ---------------------------------------------------------------
                if required_severity is not None:
                    if active_alert is None:
                        # No existing active alert — create a new one
                        new_alert = TurbineAlert(
                            id=uuid.uuid4(),
                            turbine_name=record.turbine_name,
                            error_code=_error_code(record.turbine_name),
                            message=_alert_message(record.turbine_name, status, raw_text),
                            severity=required_severity,
                            state="OPEN",
                            first_detected=now,
                            last_notified=now,
                        )
                        session.add(new_alert)
                        logger.info(
                            "Created new %s alert for turbine %s (status: %s).",
                            required_severity,
                            record.turbine_name,
                            status,
                        )

                    elif active_alert.state == "SNOOZED":
                        # Check if the snooze window has expired
                        if active_alert.snoozed_until and now > active_alert.snoozed_until:
                            active_alert.state = "OPEN"
                            active_alert.snoozed_until = None
                            active_alert.last_notified = now
                            session.add(active_alert)
                            logger.info(
                                "Snooze expired for alert %s (turbine %s) — re-opened.",
                                active_alert.id,
                                record.turbine_name,
                            )
                        else:
                            logger.debug(
                                "Alert %s for turbine %s is still within snooze window.",
                                active_alert.id,
                                record.turbine_name,
                            )

                    elif active_alert.state in ("OPEN", "ACKNOWLEDGED"):
                        # Alert already active — no action needed (idempotent)
                        logger.debug(
                            "Active alert %s for turbine %s already in state %s — no change.",
                            active_alert.id,
                            record.turbine_name,
                            active_alert.state,
                        )
        except Exception as exc:
            logger.error(
                "Error processing state machine for turbine %s: %s",
                turbine_name,
                exc,
            )

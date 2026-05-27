"""
Development data seeder for AeroMonitor v2.4.

Seeds:
  - 5 turbines (WT-01 through WT-05) with mixed operational statuses
  - Multiple telemetry snapshots simulating 2 hours of data
  - 3 open alerts (2 CRITICAL, 1 WARNING) for immediate frontend validation

Usage:
  python scripts/seed.py

Requires DATABASE_URL environment variable (or a .env file loaded beforehand).
"""

from __future__ import annotations

import asyncio
import os
import sys
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

# Add project root to sys.path so Backend modules can be imported
sys.path.insert(0, str(Path(__file__).parent.parent))

from dotenv import load_dotenv

load_dotenv(Path(__file__).parent.parent / ".env")

from Backend.database import async_session_factory, create_db_and_tables
from Backend.models import TurbineAlert, TurbineTelemetry

# ---------------------------------------------------------------------------
# Seed data definitions
# ---------------------------------------------------------------------------

_NOW = datetime.now(timezone.utc)

TURBINES = [
    {
        "card_no": "1",
        "turbine_name": "WT-01",
        "status": "OPERATIONAL",
        "today_kwh": Decimal("14250.75"),
        "wind_speed": Decimal("9.2"),
        "kw": Decimal("1850.00"),
    },
    {
        "card_no": "2",
        "turbine_name": "WT-02",
        "status": "OPERATIONAL",
        "today_kwh": Decimal("13980.50"),
        "wind_speed": Decimal("8.7"),
        "kw": Decimal("1760.00"),
    },
    {
        "card_no": "3",
        "turbine_name": "WT-03",
        "status": "FAILURE",
        "today_kwh": Decimal("4120.00"),
        "wind_speed": Decimal("7.5"),
        "kw": Decimal("0.00"),
    },
    {
        "card_no": "4",
        "turbine_name": "WT-04",
        "status": "MAINTENANCE",
        "today_kwh": Decimal("1850.25"),
        "wind_speed": Decimal("6.8"),
        "kw": Decimal("0.00"),
    },
    {
        "card_no": "5",
        "turbine_name": "WT-05",
        "status": "OPERATIONAL",
        "today_kwh": Decimal("15600.00"),
        "wind_speed": Decimal("11.3"),
        "kw": Decimal("2100.00"),
    },
]


def _make_telemetry_records() -> list[TurbineTelemetry]:
    """Generate 2 hours of telemetry snapshots (8 cycles × 15 min) for each turbine."""
    records: list[TurbineTelemetry] = []

    for cycle in range(8):
        cycle_time = _NOW - timedelta(minutes=15 * (7 - cycle))

        for turbine in TURBINES:
            # Vary KWh slightly each cycle
            kwh_variation = Decimal(str(round(cycle * 200.0, 2)))
            wind_variation = Decimal(str(round(cycle * 0.1, 1)))

            raw_status_text = f"Status: {turbine['status']}"
            if turbine["status"] == "FAILURE":
                raw_status_text = "Status: FAILURE | Gearbox temperature critical. Emergency brake engaged."
            elif turbine["status"] == "MAINTENANCE":
                raw_status_text = "Status: MAINTENANCE | Scheduled blade inspection in progress."

            records.append(
                TurbineTelemetry(
                    collected_at=cycle_time,
                    card_no=turbine["card_no"],
                    turbine_name=turbine["turbine_name"],
                    status=turbine["status"],
                    today_kwh=turbine["today_kwh"] + kwh_variation,
                    wind_speed=turbine["wind_speed"] + wind_variation,
                    kw=turbine["kw"],
                    turbine_datetime=cycle_time.replace(tzinfo=None),
                    raw_text=raw_status_text,
                )
            )

    return records


ALERTS = [
    TurbineAlert(
        id=uuid.uuid4(),
        turbine_name="WT-03",
        error_code="WT03-ERR-A01",
        message=(
            "Gearbox temperature exceeded critical threshold (108°C). "
            "Emergency brake automatically engaged. "
            "Immediate intervention required to prevent permanent damage."
        ),
        severity="CRITICAL",
        state="OPEN",
        first_detected=_NOW - timedelta(hours=2, minutes=15),
        last_notified=_NOW - timedelta(hours=2, minutes=15),
        acknowledged_by=None,
        snoozed_until=None,
        resolved_at=None,
    ),
    TurbineAlert(
        id=uuid.uuid4(),
        turbine_name="WT-03",
        error_code="WT03-ERR-B02",
        message=(
            "Secondary vibration sensor reporting anomalous readings (12.4 mm/s RMS). "
            "Main bearing wear suspected. Schedule inspection within 48 hours."
        ),
        severity="CRITICAL",
        state="ACKNOWLEDGED",
        first_detected=_NOW - timedelta(hours=3),
        last_notified=_NOW - timedelta(hours=1),
        acknowledged_by="Chief Engineer Rajan",
        snoozed_until=None,
        resolved_at=None,
    ),
    TurbineAlert(
        id=uuid.uuid4(),
        turbine_name="WT-04",
        error_code="WT04-ERR-C01",
        message=(
            "Turbine WT-04 is currently in scheduled maintenance mode. "
            "Blade pitch hydraulic system undergoing inspection. "
            "Expected return to service: 4 hours."
        ),
        severity="WARNING",
        state="SNOOZED",
        first_detected=_NOW - timedelta(hours=1, minutes=30),
        last_notified=_NOW - timedelta(hours=1, minutes=30),
        acknowledged_by="Maintenance Lead Kumar",
        snoozed_until=_NOW + timedelta(minutes=90),
        resolved_at=None,
    ),
]


# ---------------------------------------------------------------------------
# Seeder entry point
# ---------------------------------------------------------------------------


async def seed() -> None:
    print("Creating database tables (if not exist)...")
    await create_db_and_tables()

    async with async_session_factory() as session:
        # Clear existing seed data (idempotent re-run)
        from sqlalchemy import text as sql_text

        await session.exec(sql_text("DELETE FROM turbine_alerts"))  # type: ignore[arg-type]
        await session.exec(sql_text("DELETE FROM turbine_telemetry"))  # type: ignore[arg-type]

        print("Seeding telemetry records...")
        telemetry_records = _make_telemetry_records()
        for rec in telemetry_records:
            session.add(rec)

        print(f"  Added {len(telemetry_records)} telemetry records across 5 turbines.")

        print("Seeding alert records...")
        for alert in ALERTS:
            session.add(alert)

        print(f"  Added {len(ALERTS)} alert records (2 CRITICAL, 1 WARNING).")

        await session.commit()
        print("\nSeed complete!")
        print("  Turbines: WT-01 (OPERATIONAL), WT-02 (OPERATIONAL), WT-03 (FAILURE),")
        print("            WT-04 (MAINTENANCE), WT-05 (OPERATIONAL)")
        print("  Alerts:   WT-03: 2 active (OPEN + ACKNOWLEDGED)")
        print("            WT-04: 1 snoozed WARNING")


if __name__ == "__main__":
    asyncio.run(seed())

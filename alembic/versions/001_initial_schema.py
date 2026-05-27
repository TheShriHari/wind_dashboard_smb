"""
Initial schema migration for AeroMonitor v2.4.

Creates:
  - turbine_telemetry table (append-only time-series)
  - turbine_alerts table (alert lifecycle with state machine)
  - Necessary indexes for query performance
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Ensure pgcrypto is available for gen_random_uuid()
    op.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")

    # turbine_telemetry
    op.create_table(
        "turbine_telemetry",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column(
            "collected_at",
            sa.TIMESTAMP(timezone=True),
            server_default=sa.text("NOW()"),
            nullable=False,
        ),
        sa.Column("card_no", sa.String(length=10), nullable=True),
        sa.Column("turbine_name", sa.String(length=100), nullable=True),
        sa.Column("status", sa.String(length=50), nullable=True),
        sa.Column("today_kwh", sa.Numeric(precision=14, scale=2), nullable=True),
        sa.Column("wind_speed", sa.Numeric(precision=8, scale=2), nullable=True),
        sa.Column("kw", sa.Numeric(precision=12, scale=2), nullable=True),
        sa.Column("turbine_datetime", sa.TIMESTAMP(), nullable=True),
        sa.Column("raw_text", sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "idx_turbine_telemetry_name",
        "turbine_telemetry",
        ["turbine_name"],
    )
    op.create_index(
        "idx_turbine_telemetry_collected_at",
        "turbine_telemetry",
        [sa.text("collected_at DESC")],
    )

    # turbine_alerts
    op.create_table(
        "turbine_alerts",
        sa.Column(
            "id",
            sa.UUID(),
            server_default=sa.text("gen_random_uuid()"),
            nullable=False,
        ),
        sa.Column("turbine_name", sa.String(length=100), nullable=False),
        sa.Column("error_code", sa.String(length=30), nullable=True),
        sa.Column("message", sa.Text(), nullable=True),
        sa.Column("severity", sa.String(length=20), nullable=False),
        sa.Column("state", sa.String(length=20), server_default="OPEN", nullable=False),
        sa.Column(
            "first_detected",
            sa.TIMESTAMP(timezone=True),
            server_default=sa.text("NOW()"),
            nullable=False,
        ),
        sa.Column("last_notified", sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column("acknowledged_by", sa.String(length=100), nullable=True),
        sa.Column("snoozed_until", sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column("resolved_at", sa.TIMESTAMP(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "idx_turbine_alerts_turbine_name",
        "turbine_alerts",
        ["turbine_name"],
    )
    op.create_index(
        "idx_turbine_alerts_state",
        "turbine_alerts",
        ["state"],
    )
    op.create_index(
        "idx_turbine_alerts_severity_detected",
        "turbine_alerts",
        ["severity", sa.text("first_detected DESC")],
    )


def downgrade() -> None:
    op.drop_table("turbine_alerts")
    op.drop_table("turbine_telemetry")

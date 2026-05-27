# Change 05 — Full Architecture Design Specification

## 1. Database DDL

### Table: `turbine_telemetry`

```sql
CREATE TABLE turbine_telemetry (
    id               SERIAL PRIMARY KEY,
    collected_at     TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    card_no          VARCHAR(10),
    turbine_name     VARCHAR(100),
    status           VARCHAR(50),
    today_kwh        NUMERIC(14, 2),
    wind_speed       NUMERIC(8, 2),
    kw               NUMERIC(12, 2),
    turbine_datetime TIMESTAMP,
    raw_text         TEXT
);

CREATE INDEX idx_turbine_telemetry_name ON turbine_telemetry (turbine_name);
CREATE INDEX idx_turbine_telemetry_collected_at ON turbine_telemetry (collected_at DESC);
```

**Status enum values (enforced at application layer):**
- `OPERATIONAL` — turbine running normally
- `FAILURE` — major hardware/software failure
- `MAINTENANCE` — scheduled or unscheduled maintenance
- `OFFLINE` — no signal / powered down
- `UNKNOWN` — ingestion failure, status indeterminate

### Table: `turbine_alerts`

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE turbine_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    turbine_name    VARCHAR(100) NOT NULL,
    error_code      VARCHAR(30),
    message         TEXT,
    severity        VARCHAR(20) NOT NULL,  -- 'CRITICAL' | 'WARNING'
    state           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    first_detected  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_notified   TIMESTAMPTZ,
    acknowledged_by VARCHAR(100),          -- nullable
    snoozed_until   TIMESTAMPTZ,           -- nullable
    resolved_at     TIMESTAMPTZ            -- nullable
);

CREATE INDEX idx_turbine_alerts_turbine_name ON turbine_alerts (turbine_name);
CREATE INDEX idx_turbine_alerts_state ON turbine_alerts (state);
CREATE INDEX idx_turbine_alerts_severity_detected ON turbine_alerts (severity, first_detected DESC);
```

**State machine transitions:**
```
OPEN ──────────────────────────────────→ RESOLVED
 │                                           ↑
 ├──[acknowledge]──→ ACKNOWLEDGED ───────────┤
 │                                           ↑
 └──[snooze]──→ SNOOZED                      │
                  │                          │
                  └──[expiry]──→ OPEN ───────┘
                  └──[operational]──────────→┘
```

**Error code format:** `WT{N}-ERR-{CODE}` (e.g., `WT18-ERR-A04`)

---

## 2. API Route Specifications

### Base URL
- Development: `http://localhost:8000`
- Production (via nginx proxy): `http://localhost/api/...`

### `GET /api/telemetry/live`

Returns the single latest telemetry record per turbine.

**Query Implementation:**
```sql
SELECT DISTINCT ON (turbine_name) *
FROM turbine_telemetry
ORDER BY turbine_name, collected_at DESC;
```

**Response Schema:**
```json
{
  "turbines": [
    {
      "card_no": "1",
      "turbine_name": "WT-01",
      "status": "OPERATIONAL",
      "today_kwh": 1240.5,
      "wind_speed": 8.3,
      "kw": 420.0,
      "turbine_datetime": "2025-06-15T14:30:00",
      "collected_at": "2025-06-15T14:30:05Z"
    }
  ],
  "farm_summary": {
    "total_turbines": 5,
    "operational": 4,
    "failed": 1,
    "total_kwh_today": 6100.0
  }
}
```

**Farm Summary Aggregations:**
* `"failed"` counts turbines with status `IN ('FAILURE', 'FAILED', 'ERROR', 'OFFLINE', 'MAINTENANCE', 'UNKNOWN')`.
* `"operational"` counts turbines with status `= 'OPERATIONAL'`.

---

### `GET /api/alerts/active`

Returns all alerts with `state IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED')`.
Sorted: CRITICAL first, then `first_detected DESC`.

**Response Schema:**
```json
{
  "alerts": [
    {
      "id": "uuid",
      "turbine_name": "WT-03",
      "error_code": "WT03-ERR-A01",
      "message": "Gearbox temperature exceeding threshold. Emergency brake engaged.",
      "severity": "CRITICAL",
      "state": "OPEN",
      "first_detected": "2025-06-15T14:00:00Z",
      "last_notified": "2025-06-15T14:00:00Z",
      "acknowledged_by": null,
      "snoozed_until": null
    }
  ],
  "counts": {
    "OPEN": 1,
    "ACKNOWLEDGED": 0,
    "SNOOZED": 0
  }
}
```

---

### `POST /api/alerts/{id}/acknowledge`

**Request Body:**
```json
{ "operator": "John Smith" }
```

**Actions:**
- Set `state = 'ACKNOWLEDGED'`
- Set `acknowledged_by = operator`
- Set `last_notified = NOW()`

**Response:**
```json
{ "success": true, "alert": { ...updated alert object } }
```

**Error Codes:**
- `404` — alert not found
- `409` — alert already RESOLVED

---

### `POST /api/alerts/{id}/snooze`

**Request Body:**
```json
{ "duration_minutes": 15 }
```

Valid values: `15`, `30`, `60`

**Actions:**
- Set `state = 'SNOOZED'`
- Set `snoozed_until = NOW() + interval '${duration_minutes} minutes'`

**Response:**
```json
{ "success": true, "snoozed_until": "2025-06-15T15:00:00Z" }
```

**Error Codes:**
- `400` — invalid duration (not 15/30/60)
- `409` — alert already RESOLVED

---

### `GET /api/alerts/history`

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | int | 1 | Page number |
| per_page | int | 25 | Records per page (max 100) |
| severity | string | null | Filter: CRITICAL \| WARNING |
| state | string | null | Filter: OPEN \| ACKNOWLEDGED \| SNOOZED \| RESOLVED |

**Response Schema:**
```json
{
  "total_count": 142,
  "page": 1,
  "per_page": 25,
  "total_pages": 6,
  "alerts": [ ...alert objects with all fields ]
}
```

---

## 3. Alert State Machine

```
Input: List of TurbineTelemetry records (from one ingestion cycle)

FOR each record in records:
  BEGIN NESTED TRANSACTION (SAVEPOINT):
    TRY:
      IF record.status IN ['FAILURE', 'FAILED', 'ERROR']:
        severity = 'CRITICAL'
      ELIF record.status IN ['MAINTENANCE', 'OFFLINE']:
        severity = 'WARNING'
      ELIF 'not running' in record.raw_text.lower():
        severity = 'CRITICAL'
      ELSE:
        severity = NULL  # Healthy status

      IF severity IS NOT NULL:
        QUERY: active_alert = SELECT * FROM turbine_alerts
               WHERE turbine_name = record.turbine_name
               AND state IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED')
               ORDER BY first_detected DESC LIMIT 1

        IF active_alert IS NULL:
          INSERT new alert (state='OPEN', severity=severity, first_detected=NOW())

        ELIF active_alert.state = 'SNOOZED':
          IF NOW() > active_alert.snoozed_until:
            UPDATE state='OPEN', snoozed_until=NULL  ← re-open after snooze expiry
          ELSE:
            SKIP (still within snooze window)

        ELIF active_alert.state IN ('OPEN', 'ACKNOWLEDGED'):
          SKIP (alert already active, no duplicate creation)

      ELIF record.status = 'OPERATIONAL':
        QUERY: active_alert = (same lookup)
        IF active_alert EXISTS:
          UPDATE state='RESOLVED', resolved_at=NOW()

      # (UNKNOWN status or other values → no action, preserve existing alert state)
      
      COMMIT NESTED TRANSACTION (RELEASE SAVEPOINT)
    EXCEPT Exception as exc:
      ROLLBACK NESTED TRANSACTION (TO SAVEPOINT)
      Log error and continue processing other turbines

COMMIT all successfully processed turbine changes in a single outer transaction per cycle
```

**Idempotency Guarantee:** The state machine checks for existing active alerts before creating new ones. Running it twice on the same records produces the same final state.

---

## 4. Ingestion Collector Flow

```
STARTUP:
  Initialize httpx.AsyncClient with cookie_jar
  Call login()
  Start APScheduler AsyncIOScheduler with 15-min interval

STATE VARIABLES:
  failure_count: Integer stored as a global module-level variable to persist across async execution cycles.

COLLECTION CYCLE (every 15 min):
  1. GET https://www.rooktec.in/wmapp
     IF response.url redirected to login page:
       Call login() → retry GET once
       IF still redirected: log error, increment failure_count, return
  
  2. Parse HTML response:
     Find div.runningdiv elements (same selector as Selenium scraper)
     For each card: extract card_no, turbine_name, status, today_kwh,
                    wind_speed, kw, turbine_datetime, raw_text
  
  3. IF no cards found (site may be JS-rendered):
     Proactively TRIGGER XHR fallback:
       FOR each target endpoint in ['/api/turbines', '/wmapp/data', '/api/status']:
         GET relative JSON data
         IF valid JSON array returned:
           Parse turbine card records from JSON payload
           BREAK (fallback successful)
     
     IF fallback also fails (no records found):
       Log warning, increment failure_count
       IF failure_count >= 3:
         UPDATE all turbines in DB to status='UNKNOWN'
       return
  
  4. ELSE: reset failure_count = 0 (successful ingest)
     Bulk INSERT records into turbine_telemetry
  
  5. Run alert state machine with new records

ERROR HANDLING:
  Any exception in cycle → log to ingestion.log, never re-raise
  RotatingFileHandler: 7 files × 1-day retention
```

---

## 5. SQLModel Python Definitions

```python
class TurbineTelemetry(SQLModel, table=True):
    __tablename__ = "turbine_telemetry"
    id: Optional[int] = Field(default=None, primary_key=True)
    collected_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        sa_type=DateTime(timezone=True)
    )
    card_no: Optional[str] = Field(default=None, max_length=10)
    turbine_name: Optional[str] = Field(default=None, max_length=100, index=True)
    status: Optional[str] = Field(default=None, max_length=50)
    today_kwh: Optional[Decimal] = Field(default=None)
    wind_speed: Optional[Decimal] = Field(default=None)
    kw: Optional[Decimal] = Field(default=None)
    turbine_datetime: Optional[datetime] = Field(default=None)
    raw_text: Optional[str] = Field(default=None)

class TurbineAlert(SQLModel, table=True):
    __tablename__ = "turbine_alerts"
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    turbine_name: str = Field(max_length=100)
    error_code: Optional[str] = Field(default=None, max_length=30)
    message: Optional[str] = Field(default=None)
    severity: str = Field(max_length=20)
    state: str = Field(default="OPEN", max_length=20)
    first_detected: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        sa_type=DateTime(timezone=True)
    )
    last_notified: Optional[datetime] = Field(default=None, sa_type=DateTime(timezone=True))
    acknowledged_by: Optional[str] = Field(default=None, max_length=100)
    snoozed_until: Optional[datetime] = Field(default=None, sa_type=DateTime(timezone=True))
    resolved_at: Optional[datetime] = Field(default=None, sa_type=DateTime(timezone=True))
```

---

## 6. Docker Compose Service Graph

```
┌─────────────────────────────────────────────────────┐
│  docker-compose.yaml                                │
│                                                     │
│  ┌──────────┐    ┌──────────────┐    ┌───────────┐ │
│  │    db    │←───│   backend    │    │   nginx   │ │
│  │ postgres │    │  FastAPI     │    │           │ │
│  │ :5432    │    │  :8000       │    │  :80/:443 │ │
│  └──────────┘    └──────────────┘    └─────┬─────┘ │
│       ↑                 ↑                  │       │
│  pgdata volume    logs volume      /api/* ─┘proxy  │
│                                    /      static   │
└─────────────────────────────────────────────────────┘
```

---

## 7. Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `DATABASE_URL` | asyncpg connection URL | `postgresql+asyncpg://aero:secure_pass@db/aeromedia` |
| `ROOKTEC_USERNAME` | Portal login username | `smb` |
| `ROOKTEC_PASSWORD` | Portal login password | `wind@smb` |
| `ROOKTEC_URL` | Portal base URL | `https://www.rooktec.in/wmapp` |
| `COLLECTION_INTERVAL_MINUTES` | Ingestion interval | `15` |
| `CORS_ALLOWED_ORIGINS` | Comma-sep origins for prod | `*` (dev) |
| `LOG_LEVEL` | Python logging level | `INFO` |

---

## 8. Backend Dependencies

```toml
[project]
dependencies = [
    "fastapi>=0.111",
    "uvicorn[standard]>=0.30",
    "sqlmodel>=0.0.19",
    "asyncpg>=0.29",
    "httpx>=0.27",
    "beautifulsoup4>=4.12",
    "lxml>=5.0",          # BS4 parser — without this BS4 defaults to html.parser which is slower
    "apscheduler>=3.10",
    "alembic>=1.13",
    "python-dotenv>=1.0",
]
```

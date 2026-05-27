# AeroMonitor v2.4 (Wind Farm Alpha Analytics Platform)
## System Architecture Audit & Technical Specification Document

This document represents the definitive, production-grade technical specification and system architecture audit for the **AeroMonitor v2.4** ecosystem. It provides an exhaustive file-by-file blueprint detailing the ingestion engine, relational database model, state machine alert lifecycle, and complete API gateway boundaries.

---

## 1. Executive System Overview & Technology Stack

### 1.1 System Mission
AeroMonitor v2.4 marks the total decommissioning of legacy, synchronous Selenium/Chrome browser automation loops and fragile Excel flat-file storage engines. The core mission of the platform is to establish a high-performance, asynchronous, non-blocking, and state-managed relational event-driven monitoring system. It guarantees continuous high-frequency telemetry ingestion from wind farm assets, tracks stateful turbine alert lifecycles, and serves as the data foundation for operational dashboards. 

### 1.2 Unified Component Matrix
The table below maps every logical tier of the AeroMonitor v2.4 implementation, detailing the frameworks, engines, and primary file paths involved:

| Layer | Framework / Technology Used | Primary File Paths |
| :--- | :--- | :--- |
| **Ingestion Tier** | Python `httpx` (async HTTP client), `BeautifulSoup4` (DOM parser), `apscheduler` (AsyncIOScheduler) | [`Backend/ingestion/collector.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/ingestion/collector.py) |
| **Relational Core** | PostgreSQL 16 (Alpine-based container), SQLModel (SQLAlchemy ORM + Pydantic layer), `asyncpg` (non-blocking async PG driver) | [`Backend/database.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/database.py)<br>[`Backend/models.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/models.py) |
| **Alert Engine** | Stateful Logic Automation (idempotent, z-nested transactional saves) | [`Backend/alerts/state_machine.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/alerts/state_machine.py) |
| **API Gateway** | FastAPI, Uvicorn, Pydantic v2 | [`Backend/main.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/main.py) |
| **Reverse Proxy & Routing** | Nginx (Alpine-based, HTTP/80, HTTPS/443 mapping, security header injector) | [`nginx/nginx.conf`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/nginx/nginx.conf) |
| **Database Migrations** | Alembic (Database version control) | [`alembic/versions/001_initial_schema.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/alembic/versions/001_initial_schema.py)<br>[`alembic/env.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/alembic/env.py) |
| **Container Orchestration** | Docker, Docker Compose v2 | [`docker-compose.yaml`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/docker-compose.yaml)<br>[`Backend/Dockerfile`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/Dockerfile) |
| **Frontend Client** | Vanilla HTML5, CSS3, modern JS runtime, Tailwind CSS CDN integration | [`frontend/Dashboard_Code.html`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/frontend/Dashboard_Code.html)<br>[`frontend/Alert_Notification_Code.html`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/frontend/Alert_Notification_Code.html)<br>[`frontend/History_Log_Code.html`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/frontend/History_Log_Code.html) |

---

## 2. OpenSpec Design Compliance Tracking

### 2.1 Audit Directory Tree
The compliance architecture is stored within the `openspec/` root, which provides design blueprints, proposals, and task checklists. The layout of the audit path is structured as follows:

```
openspec/
└── changes/
    └── 05_wind_backend_alerts/
        ├── proposal.md     # Architectural migration goals & problem statement
        ├── design.md       # Relational database, REST, and state machine blueprints
        └── tasks.md        # Technical execution ledger & checklist trace
```

### 2.2 Specification Alignment & Checklist Execution Lineage
The system features tight alignment between the structural design documents and the operational codebase. High-fidelity verification reveals the following correlations:

* **Ingestion Replacement:** `proposal.md` outlines replacing Selenium/Chrome with an async HTTP client. The actual implementation in `collector.py` utilizes a custom `httpx.AsyncClient` with a shared cookie jar and Beautiful Soup 4 card extraction, eliminating web-driver overhead.
* **State Machine Alignment:** `design.md` maps out the state transitions (OPEN ➔ ACKNOWLEDGED ➔ SNOOZED ➔ RESOLVED) and z-nested transactional handling. This logic is fully realized in `state_machine.py`, where SQLAlchemy savepoints (`session.begin_nested()`) are utilized to isolate operations for individual turbine processing.
* **REST Service Contracts:** The endpoints specified in `design.md` match the final routing boundaries implemented in `main.py` (specifically matching return typings, parameters, and HTTP error code pathways).

#### Ingress Checklist Execution Summary (from `tasks.md`):
1. **Phase 0 (OpenSpec):** Proposal, design, and task checklists initialized.
2. **Phase 1 (PostgreSQL Schema):** `TurbineTelemetry` and `TurbineAlert` SQLModel entities successfully defined. Alembic async migrations authored, executed, and validated with indexes.
3. **Phase 2 (Ingestion Engine):** Multi-stage httpx engine deployed, integrated with a rotating file logger and consecutive failure handling logic (3 failures triggers `UNKNOWN` status write-backs).
4. **Phase 3 (Alert State Machine):** Idempotent state transitions implemented, verified, and isolated via z-nested transaction context managers.
5. **Phase 4 (FastAPI REST Layer):** 5 core routes established, fully utilizing PostgreSQL's `DISTINCT ON` dialect and multi-attribute Pydantic models.
6. **Phase 5 (Frontend API Wiring):** Native HTML views integrated with REST interfaces, replacing the static mock data with continuous, responsive HTTP polling.
7. **Phase 6 & 7 (Infrastructure & Verification):** Multi-container Docker Compose build validated; all services tested and verified in high-availability mock execution.
8. **Phase 8 (Infinite Snooze Bugfix):** Patched snooze tracking triggers and button event handling.

---

## 3. Relational Data Modeling & Schema Blueprint

### 3.1 Database Target Engine & Connections
* **Dialect:** PostgreSQL 16
* **Driver:** `asyncpg` (Python asyncio-compatible PostgreSQL driver)
* **Connection Pooling Strategy:** Instantiated via `create_async_engine()` in `Backend/database.py`:
  * `pool_size=10` (Default connection pool size to minimize handshake overhead).
  * `max_overflow=20` (Number of connections that can be opened beyond `pool_size` under peak load).
  * `pool_pre_ping=True` (Liveness checks executed before dispatching connection queries to detect stale sockets).
* **Session Management:** Async DB sessions are orchestrated via Pydantic/SQLAlchemy factories. FastAPI handles incoming request sessions using the dependency injection yield provider `get_session()`. Background processes (such as the ingestion scheduler scheduler) utilize the context manager `get_session_ctx()`.

### 3.2 Table Definition Specification

```
                          TURBINE_TELEMETRY (Append-Only Time Series)
             ┌──────────────────────────────────────────────────────────────────┐
             │ id (PK)           : BigInteger (Autoincrement)                   │
             │ collected_at      : TIMESTAMPTZ (server_default=NOW(), index)    │
             │ card_no           : String(10)                                   │
             │ turbine_name      : String(100) (index)                          │
             │ status            : String(50)                                   │
             │ today_kwh         : Numeric(14, 2)                               │
             │ wind_speed        : Numeric(8, 2)                                │
             │ kw                : Numeric(12, 2)                               │
             │ turbine_datetime  : TIMESTAMP                                    │
             │ raw_text          : Text                                         │
             └──────────────────────────────────────────────────────────────────┘
                                                 
                            TURBINE_ALERTS (State Machine Ledger)
             ┌──────────────────────────────────────────────────────────────────┐
             │ id (PK)           : UUID (server_default=gen_random_uuid())      │
             │ turbine_name      : String(100) (index)                          │
             │ error_code        : String(30)                                   │
             │ message           : Text                                         │
             │ severity          : String(20)                                   │
             │ state             : String(20) (server_default='OPEN', index)    │
             │ first_detected    : TIMESTAMPTZ (server_default=NOW())           │
             │ last_notified     : TIMESTAMPTZ                                  │
             │ acknowledged_by   : String(100)                                  │
             │ snoozed_until     : TIMESTAMPTZ                                  │
             │ resolved_at       : TIMESTAMPTZ                                  │
             └──────────────────────────────────────────────────────────────────┘
```

#### 3.2.1 `TurbineTelemetry` ORM Table Definition
* **Table Name:** `turbine_telemetry`
* **Objective:** Holds append-only physical telemetry captured during 15-minute polling cycles.
* **Fields & Constraint Parameters:**
  1. `id` [Primary Key]: `Optional[int]`, mapped to `sa.BigInteger()` in database DDL, auto-incremented.
  2. `collected_at`: `datetime`, database type `sa.TIMESTAMP(timezone=True)`, non-nullable. Defaults to `NOW()` (UTC).
  3. `card_no`: `Optional[str]`, `sa.String(10)`, nullable.
  4. `turbine_name`: `Optional[str]`, `sa.String(100)`, nullable, indexed for quick turbine lookups.
  5. `status`: `Optional[str]`, `sa.String(50)`, nullable. Governed by core application string constants: `OPERATIONAL`, `FAILURE`, `MAINTENANCE`, `OFFLINE`, `UNKNOWN`.
  6. `today_kwh`: `Optional[Decimal]`, mapped to `sa.Numeric(precision=14, scale=2)`, nullable.
  7. `wind_speed`: `Optional[Decimal]`, mapped to `sa.Numeric(precision=8, scale=2)`, nullable.
  8. `kw`: `Optional[Decimal]`, mapped to `sa.Numeric(precision=12, scale=2)`, nullable.
  9. `turbine_datetime`: `Optional[datetime]`, mapped to `sa.TIMESTAMP` without timezone, representing the turbine's local system time.
  10. `raw_text`: `Optional[str]`, database type `sa.Text()`, nullable. Captures full card dump for diagnostic recovery.
* **Database Indexes:**
  * `idx_turbine_telemetry_name` on `(turbine_name)`
  * `idx_turbine_telemetry_collected_at` on `(collected_at DESC)`

#### 3.2.2 `TurbineAlert` ORM Table Definition
* **Table Name:** `turbine_alerts`
* **Objective:** Tracks life cycles and operations of active and historic hardware anomalies.
* **Fields & Constraint Parameters:**
  1. `id` [Primary Key]: `uuid.UUID`, mapped to PostgreSQL UUID type. Defaults to `gen_random_uuid()` at schema layer via `pgcrypto` extension.
  2. `turbine_name`: `str`, `sa.String(100)`, non-nullable, indexed.
  3. `error_code`: `Optional[str]`, `sa.String(30)`, nullable. (Z-filled name parsing format: `WT{N}-ERR-{SuffixChar}{Counter}`).
  4. `message`: `Optional[str]`, database type `sa.Text()`, nullable.
  5. `severity`: `str`, `sa.String(20)`, non-nullable. Governed by string constraints: `CRITICAL` or `WARNING`.
  6. `state`: `str`, `sa.String(20)`, non-nullable. Default string value set to `"OPEN"`, indexed.
  7. `first_detected`: `datetime`, `sa.TIMESTAMP(timezone=True)`, non-nullable, defaults to `NOW()`.
  8. `last_notified`: `Optional[datetime]`, `sa.TIMESTAMP(timezone=True)`, nullable.
  9. `acknowledged_by`: `Optional[str]`, `sa.String(100)`, nullable.
  10. `snoozed_until`: `Optional[datetime]`, `sa.TIMESTAMP(timezone=True)`, nullable.
  11. `resolved_at`: `Optional[datetime]`, `sa.TIMESTAMP(timezone=True)`, nullable.
* **Database Indexes:**
  * `idx_turbine_alerts_turbine_name` on `(turbine_name)`
  * `idx_turbine_alerts_state` on `(state)`
  * `idx_turbine_alerts_severity_detected` on `(severity, first_detected DESC)`

### 3.3 Enumerated Configurations
Rather than using heavy PostgreSQL enum dependencies that can cause complications during subsequent updates, the system utilizes clean string-based enums controlled at the application and validation layer:

* **AlertState:**
  * `"OPEN"`: Newly logged anomaly awaiting triage.
  * `"ACKNOWLEDGED"`: Confirmed by operator, silencing alert notifications.
  * `"SNOOZED"`: Temporarily hidden from alerts; scheduled for automatic re-evaluation.
  * `"RESOLVED"`: Restored to service; closed and retained for historical reporting.
* **AlertSeverity:**
  * `"CRITICAL"`: Triggered by system crashes, power failures, or hardware trips.
  * `"WARNING"`: Triggered by scheduled offline/maintenance intervals.

### 3.4 Migration Audit (Alembic DDL Steps)
The structural database initialization is handled via code-based Alembic migration steps in `001_initial_schema.py`. The execution consists of:
1. **Pgcrypto Bootstrapping:** Executes a raw SQL injection `CREATE EXTENSION IF NOT EXISTS pgcrypto` to enable secure UUID generations.
2. **Telemetry Table Creation:** Compiles `turbine_telemetry` table creation DDL, assigning variable sizes to decimal parameters (`today_kwh` as Numeric 14,2, `wind_speed` as Numeric 8,2, `kw` as Numeric 12,2) and generating high-performance indexes `idx_turbine_telemetry_name` and `idx_turbine_telemetry_collected_at`.
3. **Alerts Table Creation:** Compiles `turbine_alerts` table creation, establishing UUID defaults, string length constraints, and indexing parameters `idx_turbine_alerts_turbine_name`, `idx_turbine_alerts_state`, and `idx_turbine_alerts_severity_detected`.

---

## 4. Asynchronous Data Ingestion Pipeline & Workflow

### 4.1 Authentication Handshake
Ingestion operations in [`Backend/ingestion/collector.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/ingestion/collector.py) replace the Selenium browser wrapper with a multi-stage async session using an `httpx.AsyncClient` with automatic cookie jar parsing:

```
                  AUTHENTICATION HANDSHAKE LOGICAL FLOW
  
  ┌───────────────┐        GET /wmapp        ┌─────────────────────┐
  │ Ingestion     │ ───────────────────────> │  Rooktec Web Server │
  │ Engine        │ <─────────────────────── │                     │
  └───────────────┘    Cookies & CSRF Token  └─────────────────────┘
          │
          ▼
   Extract CSRF Meta
   Prepare Form Payload
   (user_nameTxt / pass_wordTxt)
          │
          ▼
  ┌───────────────┐     POST /wmapp/login    ┌─────────────────────┐
  │ Ingestion     │ ───────────────────────> │  Rooktec Web Server │
  │ Engine        │ <─────────────────────── │                     │
  └───────────────┘   Redirect to Dashboard  └─────────────────────┘
```

1. **Initial Harvest (GET):** The engine issues a `GET` request targeting `ROOKTEC_URL` (`https://www.rooktec.in/wmapp`). The cookies and CSRF tokens are captured directly within the client's internal `cookie_jar`.
2. **CSRF Extraction Logic (`_extract_csrf_token`):** The response HTML is parsed using BeautifulSoup. The engine searches for form fields named `"csrf_token"`, `"_token"`, `"csrfmiddlewaretoken"`, or `"__RequestVerificationToken"`, and scans the HTML metadata for `<meta name="csrf-token">` to extract any CSRF credentials.
3. **Target Form Discovery:** The HTML form wrapper is parsed to find its relative action URL. If present, the post target is resolved against the baseline scheme; if absent, the engine defaults to posting to the initial login path.
4. **Credential POST Execution:** An authentication payload is prepared:
   ```python
   form_data = {
       "user_nameTxt": ROOKTEC_USERNAME,
       "pass_wordTxt": ROOKTEC_PASSWORD,
       "submit": "Log in",
   }
   ```
   If a CSRF token was detected, the engine injects it into `form_data` under the keys `csrfmiddlewaretoken` and `_token`.
5. **Validation Matrix:** The engine posts the payload with a referencing `Referer` header. The resulting redirect destination URL is evaluated: if the browser landing URL contains the text `"login"` or `"signin"`, authentication is flagged as failed. Otherwise, a persistent session is successfully registered.

### 4.2 Data Extraction Engine
Once logged in, the dashboard HTML is fetched and parsed via beautifulsoup DOM structures. This replicates the exact logic previously executed via Selenium drivers:
* **Active Card Filtration:** Selects cards using the `div.runningdiv` CSS selector, explicitly skipping any cards containing the class name `runningdivdimmed` (which indicates disabled or offline cards).
* **Turbine Name Parsing:** Extracts the text of the first `<p>` tag within the first child `<div>` block of the card, stripping whitespaces.
* **Status Extraction:** Reads the `title` attribute or text node of the `<p>` tag in the second child `<div>` block of the card.
* **Metric Extraction Regex:**
  * **Today KWh:** Matches the text of the nested `<span>` inside elements containing the text "Today" and "Kwh". This is verified against the regex `r"^\d+(\.\d+)?$"`.
  * **Wind Speed (Ws):** Scans the parent card text block using `re.search(r"Ws\s*:\s*([0-9.,]+)", ...)` and normalizes commas into decimal periods.
  * **Active Power (Kw):** Scans the card text block using `re.search(r"Kw\s*:\s*([0-9.,]+)", ...)` and normalizes commas into decimal periods.
  * **System Timestamp:** Locates the specific `<p>` block matching the date structure `r"^\d{4}-\d{2}-\d{2},\s*\d{1,2}:\d{2}$"`, converts it to clean timestamp parameters, and maps it into a native Python timezone-naive `datetime` object representing the turbine's internal hardware clock.

### 4.3 Scheduler Cadence
* **Engine:** APScheduler `AsyncIOScheduler`
* **Polling Interval:** Runs at 15-minute intervals.
* **Lifecycle Details:**
  * Lifespan handlers defined inside [`Backend/main.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/main.py) automatically start the scheduler thread loop on FastAPI system startup.
  * The job configuration contains a startup execution directive (`next_run_time=datetime.now(timezone.utc)`), triggering a live scrape immediately on server boot.
  * `max_instances=1` guarantees that slow or hung scrape processes cannot stack up concurrent execution loops.
  * `coalesce=True` collapses multiple missed executions (e.g., due to system sleep) into a single recovery run.
  * Lifetime operations are linked to the FastAPI application lifespan, ensuring clean shutdown of background threads when the server stops.

### 4.4 Resilience & Fault Tolerance
* **Logging System:** Uses a standard `logging.getLogger("aeromdc.ingestion")`. Logger outputs are directed to stdout and structured into a rotating file handler at `Backend/logs/ingestion.log` configured with daily midnight rotations (`when="midnight"`) and a 7-day retention policy (`backupCount=7`).
* **Session Lifecycle Renewal:** When requesting dashboard payloads, redirects back to the login page are automatically caught, trigger an immediate cookie clear, establish a fresh session login, and retry the request.
* **XHR Endpoint Probing (Fallback Pathway):** If HTML parsing returns no cards (which may occur due to client-side JS SPA rendering), the engine falls back to probe known JSON endpoints:
  1. `GET {base_url}/api/turbines`
  2. `GET {base_url}/wmapp/data`
  3. `GET {base_url}/api/status`
  
  If any of these endpoints respond with a valid JSON array, the values are parsed directly and mapped to `TurbineTelemetry` entities, bypassing the HTML extraction pipeline.
* **Ingestion Dropout Hardening (`UNKNOWN` Flagging):**
  * If a cycle fails due to connection loss or empty data, a global failure counter `_consecutive_failures` is incremented.
  * Once the counter reaches the threshold (`_MAX_CONSECUTIVE_FAILURES = 3`), the engine calls `_mark_all_unknown()`.
  * This fallback queries all distinct turbine names previously recorded in the database, and bulk-inserts a new telemetry record for each with `status="UNKNOWN"` and raw text indicating "Ingestion failure — status unknown".
  * Upon a successful scrape, `_consecutive_failures` is reset to `0`.

---

## 5. Alert Lifecycle State Machine Logic

The state machine is implemented in [`Backend/alerts/state_machine.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/alerts/state_machine.py). It runs at the end of every ingestion cycle and operates inside z-nested transactions for individual turbines.

```
                            ALERT STATE TRANSITION MATRIX
  
                                  ┌───────────────┐
                                  │     OPEN      │ ────┐
                                  └───────────────┘     │
                                     │    ▲             │
                     Acknowledge     │    │ Snooze      │ Operational
                     Request         │    │ Expiration  │ Recovery
                                     ▼    │             ▼
  ┌───────────────┐               ┌───────────────┐     │  ┌───────────────┐
  │   RESOLVED    │ <──────────── │ ACKNOWLEDGED  │     ├> │   RESOLVED    │
  └───────────────┘   Operational └───────────────┘     │  └───────────────┘
          ▲           Recovery                          │          ▲
          │                                             │          │
          │                       ┌───────────────┐     │          │
          └────────────────────── │    SNOOZED    │ <───┘          │
             Operational          └───────────────┘                │
             Recovery                     │                        │
                                          └────────────────────────┘
```

### 5.1 Idempotent Ingestion Evaluation
1. **Isolation Guard:** Telemetry batches are iterated per turbine. Each check is wrapped inside `async with session.begin_nested():` to capture errors individually, ensuring failures in one turbine do not affect others.
2. **Severity Classification (`_severity_for_status`):**
   * High-priority failures (status matching `FAILURE`, `FAILED`, or `ERROR`) map to `"CRITICAL"` severity.
   * Medium-priority maintenance intervals (status matching `MAINTENANCE` or `OFFLINE`) map to `"WARNING"` severity.
   * If the status is ambiguous but raw text displays key patterns (`"not running"`, `"disconnected"`, `"fault"`, `"trip"`), severity is mapped to `"CRITICAL"`.
   * Clear operations (status matching `OPERATIONAL` and no raw text keywords) return `None`.
3. **State Query Lookup:** Resolves any existing active alert (state in `"OPEN"`, `"ACKNOWLEDGED"`, `"SNOOZED"`) for the given turbine.

### 5.2 State Transition Workflow Matrix

#### Transition 1: Anomaly Detection ➔ `OPEN`
* **Trigger:** Telemetry reports `status` indicating a failure or maintenance state (e.g., status is `FAILURE`, `MAINTENANCE`, `OFFLINE`), or raw text keywords detect a drop-out, mapping to a non-null severity (`CRITICAL` or `WARNING`).
* **Prerequisite:** No active alert exists in the DB for this turbine.
* **Action:** Inserts a new `TurbineAlert` record with:
  * `id` set to a fresh UUID.
  * `state = "OPEN"`
  * `severity` set to the detected classification (`CRITICAL` or `WARNING`).
  * `error_code` generated via the format `WT{N}-ERR-{SuffixChar}{Counter}`.
  * `message` formatted with details about the specific state change.
  * `first_detected` and `last_notified` set to the current UTC timestamp.

#### Transition 2: Operator Action ➔ `ACKNOWLEDGED`
* **Trigger:** An operator sends a `POST` request to the acknowledge endpoint with an operator identifier payload.
* **Prerequisite:** The target alert must exist and its state must *not* be `"RESOLVED"`.
* **Action:** Updates the active alert record:
  * `state = "ACKNOWLEDGED"`
  * `acknowledged_by` set to the operator's name.
  * `last_notified` set to the current UTC timestamp.

#### Transition 3: Operator Action ➔ `SNOOZED`
* **Trigger:** An operator sends a `POST` request to the snooze endpoint with a snooze duration (`15`, `30`, or `60` minutes).
* **Prerequisite:** The target alert must exist and its state must *not* be `"RESOLVED"`.
* **Action:** Updates the active alert record:
  * `state = "SNOOZED"`
  * `snoozed_until` calculated as `NOW() + duration_minutes` (UTC).

#### Transition 4: Return to `OPERATIONAL` ➔ `RESOLVED` (Auto-Resolution)
* **Trigger:** Ingested telemetry reports `status = "OPERATIONAL"` and severity parses as `None`.
* **Prerequisite:** An active alert exists for this turbine.
* **Action:** Updates the active alert record:
  * `state = "RESOLVED"`
  * `resolved_at` set to the current UTC timestamp.

### 5.3 Suppression & Re-escalation Engine
* **Suppression Logic:** While an alert is in the `"SNOOZED"` state, it remains active but is suppressed from UI modals (based on the dashboard frontend query filtering).
* **Re-escalation Mathematics:** During each ingestion loop, if an active alert is in the `"SNOOZED"` state, the state machine evaluates the snooze window:
  ```python
  if active_alert.snoozed_until and now > active_alert.snoozed_until:
      active_alert.state = "OPEN"
      active_alert.snoozed_until = None
      active_alert.last_notified = now
  ```
  If `now` exceeds `snoozed_until`, the snooze window is expired, the snooze state is cleared, and the alert is re-opened to `"OPEN"`, bringing it back to the top of the operator's notification queue.

---

## 6. REST API Service Contract Directory

This section provides the complete API service contract for the AeroMonitor v2.4 API registered in [`Backend/main.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/Backend/main.py).

### 6.1 `GET /api/telemetry/live`

* **Endpoint Signature:** `GET /api/telemetry/live`
* **Description:** Retrieves the latest telemetry record for each turbine alongside aggregated farm summary KPIs.
* **Request Specifications:**
  * **Headers:** `Accept: application/json`
  * **Query Parameters:** None.
* **Response Specifications (200 OK):**
  * **Content Type:** `application/json`
  * **Response Body Schema (`LiveTelemetryResponse`):**
    ```json
    {
      "turbines": [
        {
          "card_no": "1",
          "turbine_name": "WT-01",
          "status": "OPERATIONAL",
          "today_kwh": 14250.75,
          "wind_speed": 9.20,
          "kw": 1850.00,
          "turbine_datetime": "2026-05-27T17:30:00",
          "collected_at": "2026-05-27T17:31:00Z"
        }
      ],
      "farm_summary": {
        "total_turbines": 5,
        "operational": 4,
        "failed": 1,
        "total_kwh_today": 35681.50
      }
    }
    ```
* **Database Query Mechanics:**
  Uses a raw SQL query with PostgreSQL's `DISTINCT ON` dialect to retrieve only the single latest telemetry record per turbine in a single pass:
  ```sql
  SELECT DISTINCT ON (turbine_name)
      id, collected_at, card_no, turbine_name, status,
      today_kwh, wind_speed, kw, turbine_datetime, raw_text
  FROM turbine_telemetry
  WHERE turbine_name IS NOT NULL
  ORDER BY turbine_name, collected_at DESC;
  ```
  The returned records are then parsed and aggregated:
  * `total_turbines`: Total count of distinct records returned.
  * `failed`: Count of turbines with `status` in `("FAILURE", "FAILED", "ERROR", "OFFLINE", "MAINTENANCE", "UNKNOWN")`.
  * `operational`: Count of turbines with `status == "OPERATIONAL"`.
  * `total_kwh_today`: Sum of all `today_kwh` values, rounded to 2 decimal places.

---

### 6.2 `GET /api/alerts/active`

* **Endpoint Signature:** `GET /api/alerts/active`
* **Description:** Returns a list of all active, unresolved alerts (states `"OPEN"`, `"ACKNOWLEDGED"`, or `"SNOOZED"`).
* **Request Specifications:**
  * **Headers:** `Accept: application/json`
  * **Query Parameters:** None.
* **Response Specifications (200 OK):**
  * **Content Type:** `application/json`
  * **Response Body Schema (`ActiveAlertsResponse`):**
    ```json
    {
      "alerts": [
        {
          "id": "e0bfa9c8-4720-410f-87a2-b2587f2e15fb",
          "turbine_name": "WT-03",
          "error_code": "WT03-ERR-A01",
          "message": "Gearbox temperature exceeded critical threshold (108°C).",
          "severity": "CRITICAL",
          "state": "OPEN",
          "first_detected": "2026-05-27T15:16:07Z",
          "last_notified": "2026-05-27T15:16:07Z",
          "acknowledged_by": null,
          "snoozed_until": null,
          "resolved_at": null
        }
      ],
      "counts": {
        "OPEN": 1,
        "ACKNOWLEDGED": 0,
        "SNOOZED": 0
      }
    }
    ```
* **Database Query Mechanics:**
  Queries the `turbine_alerts` table using an ORM selection with a custom sorting order. It prioritizes `"OPEN"` alerts over `"ACKNOWLEDGED"` and `"SNOOZED"`, places `"CRITICAL"` severity first, and sorts by detection time descending:
  ```python
  select(TurbineAlert)
  .where(TurbineAlert.state.in_(["OPEN", "ACKNOWLEDGED", "SNOOZED"]))
  .order_by(
      text("CASE WHEN state = 'OPEN' THEN 0 WHEN state = 'ACKNOWLEDGED' THEN 1 ELSE 2 END"),
      text("CASE WHEN severity = 'CRITICAL' THEN 0 ELSE 1 END"),
      TurbineAlert.first_detected.desc()
  )
  ```

---

### 6.3 `POST /api/alerts/{alert_id}/acknowledge`

* **Endpoint Signature:** `POST /api/alerts/{alert_id}/acknowledge`
* **Description:** Acknowledges an active alert, updating its state and recording the operator's name.
* **Request Specifications:**
  * **Path Parameters:**
    * `alert_id` (UUID, required): The unique identifier of the target alert.
  * **Request Body (`AcknowledgeRequest`):**
    ```json
    {
      "operator": "John Smith"
    }
    ```
* **Response Specifications (200 OK):**
  * **Content Type:** `application/json`
  * **Response Body Schema (`AcknowledgeResponse`):**
    ```json
    {
      "success": true,
      "alert": {
        "id": "e0bfa9c8-4720-410f-87a2-b2587f2e15fb",
        "turbine_name": "WT-03",
        "error_code": "WT03-ERR-A01",
        "message": "Gearbox temperature exceeded critical threshold (108°C).",
        "severity": "CRITICAL",
        "state": "ACKNOWLEDGED",
        "first_detected": "2026-05-27T15:16:07Z",
        "last_notified": "2026-05-27T17:31:07Z",
        "acknowledged_by": "John Smith",
        "snoozed_until": null,
        "resolved_at": null
      }
    }
    ```
* **Database Query Mechanics:**
  1. Fetches the alert record by ID: `session.get(TurbineAlert, alert_id)`.
  2. If the alert is not found, raises a **404 Not Found** error.
  3. If the alert is already in the `"RESOLVED"` state, raises a **409 Conflict** error.
  4. Updates the alert's state to `"ACKNOWLEDGED"`, sets `acknowledged_by` to the provided operator name, and sets `last_notified` to the current UTC timestamp.
  5. Commits the transaction and returns the updated record.

* **Exceptional Error Handlings:**
  * **404 Not Found:**
    ```json
    { "detail": "Alert e0bfa9c8-4720-410f-87a2-b2587f2e15fb not found." }
    ```
  * **409 Conflict:**
    ```json
    { "detail": "Alert e0bfa9c8-4720-410f-87a2-b2587f2e15fb is already RESOLVED and cannot be acknowledged." }
    ```

---

### 6.4 `POST /api/alerts/{alert_id}/snooze`

* **Endpoint Signature:** `POST /api/alerts/{alert_id}/snooze`
* **Description:** Snoozes an active alert, temporarily suppressing it from operator views for a specified duration (15, 30, or 60 minutes).
* **Request Specifications:**
  * **Path Parameters:**
    * `alert_id` (UUID, required): The unique identifier of the target alert.
  * **Request Body (`SnoozeRequest`):**
    ```json
    {
      "duration_minutes": 30
    }
    ```
* **Response Specifications (200 OK):**
  * **Content Type:** `application/json`
  * **Response Body Schema (`SnoozeResponse`):**
    ```json
    {
      "success": true,
      "snoozed_until": "2026-05-27T18:01:07Z"
    }
    ```
* **Database Query Mechanics:**
  1. Fetches the alert record by ID: `session.get(TurbineAlert, alert_id)`.
  2. If the alert is not found, raises a **404 Not Found** error.
  3. If the alert is already in the `"RESOLVED"` state, raises a **409 Conflict** error.
  4. Calculates `snoozed_until` as `now + duration_minutes`.
  5. Updates the alert's state to `"SNOOZED"` and sets `snoozed_until` to the calculated timestamp.
  6. Commits the transaction and returns the updated record.

* **Exceptional Error Handlings:**
  * **400 Bad Request:** Triggered if `duration_minutes` is not `15`, `30`, or `60`.
    ```json
    {
      "detail": [
        {
          "type": "value_error",
          "loc": ["body", "duration_minutes"],
          "msg": "Value error, duration_minutes must be 15, 30, or 60",
          "input": 45
        }
      ]
    }
    ```
  * **404 Not Found:**
    ```json
    { "detail": "Alert e0bfa9c8-4720-410f-87a2-b2587f2e15fb not found." }
    ```
  * **409 Conflict:**
    ```json
    { "detail": "Alert e0bfa9c8-4720-410f-87a2-b2587f2e15fb is already RESOLVED and cannot be snoozed." }
    ```

---

### 6.5 `GET /api/alerts/history`

* **Endpoint Signature:** `GET /api/alerts/history`
* **Description:** Retrieves a paginated list of historical and active alerts, with optional filtering by severity and state.
* **Request Specifications:**
  * **Query Parameters:**
    * `page` (Integer, optional, default: `1`, minimum: `1`): The target page number.
    * `per_page` (Integer, optional, default: `25`, minimum: `1`, maximum: `100`): The number of records to return per page.
    * `severity` (String, optional): Filter by severity (`CRITICAL` or `WARNING`).
    * `state` (String, optional): Filter by state (`OPEN`, `ACKNOWLEDGED`, `SNOOZED`, or `RESOLVED`).
* **Response Specifications (200 OK):**
  * **Content Type:** `application/json`
  * **Response Body Schema (`AlertHistoryResponse`):**
    ```json
    {
      "total_count": 142,
      "page": 1,
      "per_page": 25,
      "total_pages": 6,
      "alerts": [
        {
          "id": "e0bfa9c8-4720-410f-87a2-b2587f2e15fb",
          "turbine_name": "WT-03",
          "error_code": "WT03-ERR-A01",
          "message": "Gearbox temperature exceeded critical threshold (108°C).",
          "severity": "CRITICAL",
          "state": "RESOLVED",
          "first_detected": "2026-05-27T15:16:07Z",
          "last_notified": "2026-05-27T15:16:07Z",
          "acknowledged_by": null,
          "snoozed_until": null,
          "resolved_at": "2026-05-27T15:31:07Z"
        }
      ]
    }
    ```
* **Database Query Mechanics:**
  1. Instantiates base queries: `select(TurbineAlert)` and a separate count query `select(func.count()).select_from(TurbineAlert)`.
  2. Dynamically applies filters: if `severity` or `state` parameters are provided, they are appended as `and_` conditions to both queries.
  3. Executes the count query to obtain `total_count` for pagination metadata.
  4. Calculates offset: `offset = (page - 1) * per_page`.
  5. Executes the paginated query, sorted by detection time descending:
     ```python
     query.order_by(TurbineAlert.first_detected.desc()).offset(offset).limit(per_page)
     ```
  6. Computes `total_pages` as `max(1, (total_count + per_page - 1) // per_page)`.

---

### 6.6 `GET /health`

* **Endpoint Signature:** `GET /health`
* **Description:** Standard health check endpoint used by Docker health checks and Nginx to verify container status.
* **Request Specifications:**
  * **Query Parameters:** None.
* **Response Specifications (200 OK):**
  * **Content Type:** `application/json`
  * **Response Body Schema:**
    ```json
    {
      "status": "ok"
    }
    ```

---

## 7. Operational Integrity & Verification

### 7.1 Multi-Container Docker Graph
The production environment runs in a containerized environment managed via Docker Compose:

```
                            CONTAINER DEPLOYMENT GRAPH
  
                     ┌───────────────────────────────────────┐
                     │           HOST NETWORK BOUNDARY       │
                     │  Ports Exposed: HTTP/80, HTTPS/443    │
                     └───────────────────────────────────────┘
                                         │
                                         ▼
                                 ┌───────────────┐
                                 │  nginx_proxy  │
                                 └───────────────┘
                                   │           │
                     Static Assets │           │ API Traffic (/api/*)
                                   ▼           ▼
                           ┌───────────┐   ┌───────────────┐
                           │ Static    │   │  fastapi_app  │
                           │ Frontends │   └───────────────┘
                           └───────────┘           │
                                                   ▼ (asyncpg / 5432)
                                           ┌───────────────┐
                                           │  postgres_db  │
                                           └───────────────┘
```

### 7.2 Database Seeding & UI Validation
The environment includes a database seeder ([`scripts/seed.py`](file:///c:/Users/toshr/OneDrive/Desktop/Do%20More/SMB_Wind_Analytics-main/scripts/seed.py)) that populates the PostgreSQL database with:
* 5 distinct wind turbines (`WT-01` through `WT-05`).
* 8 collection cycles (spanning 2 hours of telemetry data at 15-minute intervals) for each turbine.
* 3 active test alerts:
  * `WT-03`: `CRITICAL` alert in `"OPEN"` state (gearbox over-temperature).
  * `WT-03`: `CRITICAL` alert in `"ACKNOWLEDGED"` state (vibration sensor anomaly).
  * `WT-04`: `WARNING` alert in `"SNOOZED"` state (scheduled hydraulic maintenance).

This seeded data enables direct testing of UI components like the operator notification modal and historical log tables immediately upon deployment.

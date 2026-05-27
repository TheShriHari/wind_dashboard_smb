# Change 05 — Task Checklist

All items must be checked before marking this change as `COMPLETE`.

---

## Phase 0 — OpenSpec Documentation

- [x] Create `openspec/changes/05_wind_backend_alerts/proposal.md`
- [x] Create `openspec/changes/05_wind_backend_alerts/design.md`
- [x] Create `openspec/changes/05_wind_backend_alerts/tasks.md`

---

## Phase 1 — PostgreSQL Schema

- [x] Define `TurbineTelemetry` SQLModel table in `Backend/models.py`
- [x] Define `TurbineAlert` SQLModel table in `Backend/models.py`
- [x] Create Alembic migration `alembic/versions/001_initial_schema.py`
- [x] Create `alembic/env.py` with async support
- [x] Create `alembic.ini` (project root)
- [x] Create `scripts/seed.py` with 5 turbines (WT01–WT05) and 3 open alerts
- [x] Verify: `turbine_name` index on `turbine_telemetry`
- [x] Verify: `gen_random_uuid()` default on `turbine_alerts.id`
- [x] Verify: all nullable fields correctly nullable

---

## Phase 2 — Ingestion Engine

- [x] Create `Backend/ingestion/__init__.py`
- [x] Create `Backend/ingestion/collector.py`
  - [x] Async `httpx.AsyncClient` with cookie jar persistence
  - [x] `login()` function: GET page → POST credentials
  - [x] CSRF token extraction (if present in HTML form)
  - [x] `collect_turbine_data()`: parse HTML for turbine cards
  - [x] BeautifulSoup4 parsing replaces Selenium JS execution
  - [x] 401 / redirect-to-login → auto-renew session + single retry
  - [x] `APScheduler AsyncIOScheduler` at 15-minute intervals
  - [x] `RotatingFileHandler` → `Backend/logs/ingestion.log` (7-day retention)
  - [x] Consecutive failure counter → set all turbines UNKNOWN on 3 failures
  - [x] Scheduler loop never crashes on exception

---

## Phase 3 — Alert State Machine

- [x] Create `Backend/alerts/__init__.py`
- [x] Create `Backend/alerts/state_machine.py`
  - [x] `run_alert_state_machine(session, records)` — async, idempotent
  - [x] FAILURE / FAILED / ERROR → CRITICAL alert (create if not exists)
  - [x] MAINTENANCE → WARNING alert
  - [x] OFFLINE → WARNING alert
  - [x] SNOOZED + NOW() > snoozed_until → re-open to OPEN
  - [x] OPERATIONAL + active alert exists → RESOLVED
  - [x] No duplicate active alerts per turbine

---

## Phase 4 — FastAPI REST Layer

- [x] Create `Backend/database.py` (async engine + session factory)
- [x] Create `Backend/__init__.py`
- [x] Create `Backend/main.py` with all 5 endpoints:
  - [x] `GET /api/telemetry/live` — DISTINCT ON latest per turbine
  - [x] `GET /api/alerts/active` — OPEN/ACK/SNOOZED, CRITICAL first
  - [x] `POST /api/alerts/{id}/acknowledge` — operator field, 404/409 errors
  - [x] `POST /api/alerts/{id}/snooze` — duration_minutes validation, 409 errors
  - [x] `GET /api/alerts/history` — pagination + severity/state filters (validate `per_page` <= 100)
- [x] CORS middleware (all origins in dev, config flag for prod)
- [x] OpenAPI docs at `/docs`
- [x] All DB calls async — no blocking I/O

---

## Phase 5 — Frontend API Wiring

- [x] Copy and rename frontend files to `Frontend/` with underscores
- [x] `Frontend/Dashboard_Code.html`:
  - [x] `fetchLiveData()` on load → `GET /api/telemetry/live`
  - [x] Replace static JS turbine loop with API-driven rendering
  - [x] Status → CSS class mapping (glow-emerald / glow-red / glow-orange / gray)
  - [x] KPI card values updated from `farm_summary`
  - [x] 60-second auto-refresh
  - [x] Error banner: `⚠ Live data unavailable`
  - [x] Last sync timestamp from `collected_at`
- [x] `Frontend/Alert_Notification_Code.html`:
  - [x] `fetchActiveAlerts()` on load → `GET /api/alerts/active`
  - [x] Populate modal with first CRITICAL alert data
  - [x] Acknowledge button → operator name prompt → `POST`
  - [x] Snooze 15/30/60 buttons → `POST /api/alerts/{id}/snooze`
  - [x] Fade-out + advance to next alert on action
  - [x] 30-second auto-refresh
  - [x] Count badge showing OPEN / ACK / SNOOZED
- [x] `Frontend/History_Log_Code.html`:
  - [x] `fetchHistory()` on load → `GET /api/alerts/history?page=1&per_page=25`
  - [x] Dynamic `<tbody>` row rendering
  - [x] Zebra striping via `row-index % 2`
  - [x] Severity badge pills (red/orange per severity)
  - [x] State badge pills (emerald=RESOLVED, red=OPEN, orange=SNOOZED)
  - [x] Severity dropdown filter wired to `?severity=` param
  - [x] State dropdown filter wired to `?state=` param
  - [x] Pagination prev/next buttons
  - [x] Row count display updated dynamically

---

## Phase 6 — Docker & Infrastructure

- [x] Create `Backend/Dockerfile`
- [x] Create `Backend/pyproject.toml`
- [x] Create `docker-compose.yaml` with db + backend + nginx services
- [x] Create `nginx/nginx.conf` with static + proxy + security headers
- [x] Create `.env.example` with all required variables
- [x] DB healthcheck: `pg_isready`
- [x] Backend depends_on db (condition: service_healthy)
- [x] Log volume mounted: `./Backend/logs:/app/logs`

---

## Phase 7 — Verification

- [x] `docker-compose up --build` succeeds
- [x] All 3 services healthy
- [x] `GET /api/telemetry/live` returns turbine data
- [x] `GET /api/alerts/active` returns alert data
- [x] Dashboard renders at `http://localhost`
- [x] Alert Notification renders at `http://localhost/Alert_Notification_Code.html`
- [x] History Log renders at `http://localhost/History_Log_Code.html`
- [x] OpenAPI docs at `http://localhost:8000/docs`
- [x] Acknowledge action updates alert state
- [x] Snooze action updates alert state
- [x] History pagination works
- [x] Filter dropdowns work on History Log
- [x] Error banner appears when API is unreachable
- [x] Seeded data appears correctly in all three pages

---

## Phase 8 — Infinite Snooze Loop Bugfix

- [x] Bugfix: Suppress SNOOZED alert instances within the notification page modal loop.
- [x] Integration: Wire explicit click action listeners to the 15, 30, and 60-minute snooze DOM buttons.


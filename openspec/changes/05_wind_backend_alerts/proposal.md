# Change 05 — Wind Backend & Alert System

## Status
`PROPOSED` → `IN REVIEW` → `APPROVED` → `IMPLEMENTED`

**Current Status:** `IMPLEMENTED`
**Date:** 2026-05-27
**Author:** AeroMonitor Engineering

---

## Problem Statement

The existing data pipeline (`wind_scraper/wind_data_collector.py`) has three critical production blockers:

### 1. Selenium / Chrome Dependency
- Requires a full Chrome browser process on the host machine
- Cannot run headlessly in a Docker container without a dedicated VNC/Xvfb layer
- Chrome updates frequently break the WebDriver compatibility matrix
- Single-threaded blocking loop — one crash stops all collection

### 2. Excel as Storage Backend
- `wind_turbine_data.xlsx` grows unboundedly with no pruning
- No indexing — querying for "latest record per turbine" requires full file read
- Cannot support concurrent reads (frontend) and writes (scraper) without file locking
- No transactional integrity — partial writes can corrupt the file

### 3. No Alert Lifecycle Management
- Status changes are detected but never acted upon
- There is no record of when a turbine first failed, who acknowledged it, or when it recovered
- The three existing frontend pages (Dashboard, Alert Notification, History Log) have no data source — all values are hardcoded static placeholders

---

## Proposed Solution

### Replace Selenium with `httpx` (Async HTTP)

The rooktec.in/wmapp portal serves turbine card data via server-rendered HTML. The Selenium scraper's JavaScript extraction logic (`getWsKw`, `getTodayKwh`, etc.) can be replicated in Python using `httpx` + `BeautifulSoup4` or regex, eliminating the browser dependency entirely.

**Login Flow Replicated:**
1. `GET https://www.rooktec.in/wmapp` → capture session cookies + any CSRF token
2. `POST` credentials (`username=smb`, `password=wind@smb`) to the login endpoint
3. Maintain the `httpx.AsyncClient` with persistent cookies
4. `GET` the dashboard page and parse turbine card HTML
5. On 401 / redirect-to-login: auto-renew session and retry once

**Fallback Strategy:** If the site uses JavaScript-rendered cards (SPA pattern), the collector will probe known XHR endpoints that typically back such dashboards (e.g., `/api/turbines`, `/wmapp/data`, `/api/status`). If none respond with parseable JSON, the ingestion cycle skips and logs the failure without crashing.

### PostgreSQL as Storage Backend

Two normalized tables replace the Excel file:
- `turbine_telemetry` — append-only time-series table (one row per turbine per collection cycle)
- `turbine_alerts` — alert lifecycle table with state machine transitions

Backed by `asyncpg` for non-blocking I/O compatible with FastAPI's async runtime.

### FastAPI REST Layer

Five REST endpoints serve the three frontend pages:
- `GET /api/telemetry/live` → Dashboard turbine grid + KPI cards
- `GET /api/alerts/active` → Alert Notification modal + Dashboard right panel
- `POST /api/alerts/{id}/acknowledge` → Acknowledge modal action
- `POST /api/alerts/{id}/snooze` → Snooze modal action (15/30/60 min)
- `GET /api/alerts/history` → History Log table (paginated, filterable)

### Alert State Machine

Runs after every ingestion cycle. Idempotent — safe to run multiple times. Handles:
- New alert creation when a turbine enters FAILURE/MAINTENANCE/OFFLINE state
- Snooze expiry (automatically re-opens snoozed alerts after the snooze window)
- Auto-resolution when a turbine returns to OPERATIONAL

---

## Architecture Rationale

| Concern | Old Approach | New Approach |
|---------|-------------|--------------|
| Data collection | Selenium + Chrome | httpx async HTTP |
| Storage | Excel (.xlsx) | PostgreSQL 16 |
| Query | pandas full-file read | SQL with indexes |
| Alert tracking | None | State machine in DB |
| API layer | None | FastAPI + asyncpg |
| Scheduling | `time.sleep()` loop | APScheduler AsyncIOScheduler |
| Deployment | Manual Python run | Docker Compose |
| Frontend data | Hardcoded static | Live API fetch |

---

## Migration Strategy

1. Deploy new stack alongside existing scraper (both can run temporarily)
2. Seed database with realistic sample data for immediate frontend validation
3. Verify all three frontend pages render correctly from API
4. Confirm ingestion cycle successfully authenticates and writes records
5. Decommission Selenium scraper (archive, do not delete)

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| rooktec.in uses JS rendering | Medium | httpx fallback + documented probe endpoints |
| Session expiry during collection | Low | Auto-renew with single retry |
| PostgreSQL unavailable | Low | Docker health check + dependency ordering |
| Alert state machine double-run | Low | Idempotent design — duplicate runs are no-ops |

---

## References
- Existing scraper: `wind_scraper/wind_data_collector.py`
- Frontend pages: `frontend/Dashboard Code.html`, `frontend/Alert Notification Code.html`, `frontend/History Log Code.html`
- Prior OpenSpec entries: `openspec/changes/01` through `04`

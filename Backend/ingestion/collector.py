"""
Async ingestion collector for AeroMonitor v2.4.

Replaces the Selenium/Chrome-based wind_data_collector.py with an httpx
async HTTP client that replicates the same login flow without a browser.

Schedule: Every 15 minutes via APScheduler AsyncIOScheduler.
Logging:  Backend/logs/ingestion.log (rotating, 7-day retention).
Failure:  3 consecutive cycle failures → marks all turbines UNKNOWN in DB.
"""

from __future__ import annotations

import asyncio
import logging
import os
import re
import sys
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path
from typing import Optional

import httpx
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from bs4 import BeautifulSoup
from sqlalchemy import text
from sqlmodel import select

from Backend.alerts.state_machine import run_alert_state_machine
from Backend.database import async_session_factory
from Backend.models import TurbineTelemetry

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

ROOKTEC_URL: str = os.environ.get("ROOKTEC_URL", "https://www.rooktec.in/wmapp")
ROOKTEC_DASHBOARD_URL: str = ROOKTEC_URL.rstrip("/") + "/reload_status_tst.php"
ROOKTEC_USERNAME: str = os.environ.get("ROOKTEC_USERNAME", "smb")
ROOKTEC_PASSWORD: str = os.environ.get("ROOKTEC_PASSWORD", "wind@smb")
COLLECTION_INTERVAL_MINUTES: int = int(
    os.environ.get("COLLECTION_INTERVAL_MINUTES", "15")
)

# ---------------------------------------------------------------------------
# Logging setup
# ---------------------------------------------------------------------------

LOG_DIR = Path(os.environ.get("LOG_DIR", "/app/logs"))
LOG_DIR.mkdir(parents=True, exist_ok=True)

_log_handler = TimedRotatingFileHandler(
    filename=LOG_DIR / "ingestion.log",
    when="midnight",
    backupCount=7,
    encoding="utf-8",
)
_log_handler.setFormatter(
    logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
)

logger = logging.getLogger("aeromdc.ingestion")
logger.setLevel(logging.INFO)
logger.addHandler(_log_handler)
logger.addHandler(logging.StreamHandler(sys.stdout))

# ---------------------------------------------------------------------------
# Collector state
# ---------------------------------------------------------------------------

_client: Optional[httpx.AsyncClient] = None
_consecutive_failures: int = 0
_MAX_CONSECUTIVE_FAILURES = 3


def _make_client() -> httpx.AsyncClient:
    """Create a new httpx async client with a shared cookie jar."""
    return httpx.AsyncClient(
        timeout=httpx.Timeout(30.0),
        follow_redirects=True,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            ),
        },
    )


def _extract_csrf_token(html: str) -> tuple[Optional[str], Optional[str]]:
    """
    Extract CSRF token from a login page if present.
    Tries common hidden-input names used by Django, Laravel, Rails, etc.
    Returns (token_name, token_value) if found, otherwise (None, None).
    """
    soup = BeautifulSoup(html, "html.parser")
    for name in ("csrf_token", "_token", "csrfmiddlewaretoken", "__RequestVerificationToken"):
        tag = soup.find("input", {"name": name})
        if tag and tag.get("value"):
            return name, str(tag["value"])
    # Also check meta tags (Django REST, Rails)
    meta = soup.find("meta", {"name": "csrf-token"})
    if meta and meta.get("content"):
        return "csrf-token", str(meta["content"])
    return None, None


async def login(client: httpx.AsyncClient) -> bool:
    """
    Authenticate with the rooktec portal.

    1. GET the login page to capture session cookies + CSRF token
    2. POST credentials to the login endpoint
    3. Return True if the authenticated dashboard page is reached.
    """
    try:
        login_response = await client.get(ROOKTEC_URL)
        login_response.raise_for_status()
        csrf_name, csrf_token = _extract_csrf_token(login_response.text)

        form_data: dict[str, str] = {
            "user_nameTxt": ROOKTEC_USERNAME,
            "pass_wordTxt": ROOKTEC_PASSWORD,
            "submit": "Log in",
        }
        if csrf_name and csrf_token:
            form_data[csrf_name] = csrf_token

        # Find the form action URL (may differ from page URL)
        soup = BeautifulSoup(login_response.text, "html.parser")
        form_tag = soup.find("form")
        if form_tag and form_tag.get("action"):
            action_path = str(form_tag["action"])
            if action_path.startswith("http"):
                post_url = action_path
            elif action_path.startswith("/"):
                parsed = httpx.URL(ROOKTEC_URL)
                post_url = f"{parsed.scheme}://{parsed.host}{action_path}"
            else:
                post_url = ROOKTEC_URL.rstrip("/") + "/" + action_path.lstrip("/")
        else:
            post_url = ROOKTEC_URL

        auth_response = await client.post(
            post_url,
            data=form_data,
            headers={"Referer": ROOKTEC_URL},
        )

        # Successful login lands on the dashboard (not back on the login page)
        final_url = str(auth_response.url)
        if "login" in final_url.lower() or "signin" in final_url.lower():
            logger.error("Login failed — still on login page after POST. Check credentials.")
            return False

        logger.info("Successfully authenticated with rooktec portal.")
        return True

    except httpx.HTTPError as exc:
        logger.error("HTTP error during login: %s", exc)
        return False


def _clean(value: Optional[str]) -> str:
    """Strip whitespace and normalize non-breaking spaces."""
    if not value:
        return ""
    return re.sub(r"\s+", " ", value.replace("\u00a0", " ")).strip()


def _parse_turbine_cards(html: str) -> list[dict]:
    """
    Parse turbine card data from the rooktec dashboard HTML.

    Mirrors the JavaScript extraction logic from wind_data_collector.py:
      - div.runningdiv elements (excludes .runningdivdimmed)
      - card_no = card index (1-based)
      - turbine_name = first <p> in first child div
      - status = <p title="..."> or <p> text in second child div
      - today_kwh = span whose parent contains "Today" and "Kwh"
      - wind_speed = text matching "Ws: {number}"
      - kw = text matching "Kw: {number}"
      - turbine_datetime = <p> matching YYYY-MM-DD,HH:MM pattern
    """
    soup = BeautifulSoup(html, "html.parser")

    # Select all turbine card divs (exclude dimmed/disabled ones)
    all_cards = soup.select("div.runningdiv")
    cards = [c for c in all_cards if "runningdivdimmed" not in c.get("class", [])]

    if not cards:
        logger.warning("No turbine cards found. Page may require JavaScript rendering.")
        return []

    records = []
    collected_at = datetime.now(timezone.utc)

    for index, card in enumerate(cards):
        direct_divs = [ch for ch in card.children if getattr(ch, "name", None) == "div"]

        # turbine_name
        turbine_name = ""
        if direct_divs:
            name_p = direct_divs[0].find("p")
            if name_p:
                turbine_name = _clean(name_p.get_text())

        # status
        status = ""
        if len(direct_divs) > 1:
            status_div = direct_divs[1]
            status_p = status_div.find("p")
            if status_p:
                status = _clean(status_p.get("title") or status_p.get_text())

        # today_kwh — find span inside element containing "Today" and "Kwh"
        today_kwh: Optional[str] = None
        for span in card.find_all("span"):
            parent = span.parent
            if parent:
                parent_text = _clean(parent.get_text())
                if "Today" in parent_text and "Kwh" in parent_text:
                    val = _clean(span.get_text())
                    if re.match(r"^\d+(\.\d+)?$", val):
                        today_kwh = val
                        break

        # wind_speed + kw
        wind_speed: Optional[str] = None
        kw: Optional[str] = None
        for div in card.find_all("div"):
            text_content = _clean(div.get_text())
            if not wind_speed:
                ws_match = re.search(r"Ws\s*:\s*([0-9.,]+)", text_content, re.IGNORECASE)
                if ws_match:
                    wind_speed = ws_match.group(1).replace(",", ".")
            if not kw:
                kw_match = re.search(r"Kw\s*:\s*([0-9.,]+)", text_content, re.IGNORECASE)
                if kw_match:
                    kw = kw_match.group(1).replace(",", ".")

        # turbine_datetime — matches "YYYY-MM-DD,HH:MM"
        turbine_datetime: Optional[str] = None
        for p_tag in card.find_all("p"):
            p_text = _clean(p_tag.get_text())
            if re.match(r"^\d{4}-\d{2}-\d{2},\s*\d{1,2}:\d{2}$", p_text):
                turbine_datetime = p_text.replace(" ", "")
                break

        def _to_decimal(val: Optional[str]) -> Optional[Decimal]:
            if not val:
                return None
            try:
                return Decimal(val)
            except InvalidOperation:
                return None

        def _parse_dt(val: Optional[str]) -> Optional[datetime]:
            if not val:
                return None
            for fmt in ("%Y-%m-%d,%H:%M", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S"):
                try:
                    return datetime.strptime(val, fmt)
                except ValueError:
                    continue
            return None

        records.append(
            {
                "card_no": str(index + 1),
                "turbine_name": turbine_name or f"WT-{index + 1:02d}",
                "status": status.upper() if status else "UNKNOWN",
                "today_kwh": _to_decimal(today_kwh),
                "wind_speed": _to_decimal(wind_speed),
                "kw": _to_decimal(kw),
                "turbine_datetime": _parse_dt(turbine_datetime),
                "raw_text": _clean(card.get_text()),
                "collected_at": collected_at,
            }
        )

    logger.info("Parsed %d turbine cards from dashboard HTML.", len(records))
    return records


async def _probe_xhr_endpoints(client: httpx.AsyncClient) -> list[dict]:
    """
    Fallback strategy: probe known XHR endpoints if HTML parsing fails.
    Checks /api/turbines, /wmapp/data, and /api/status.
    """
    base_url = ROOKTEC_URL.rstrip("/")
    endpoints = ["/api/turbines", "/wmapp/data", "/api/status"]
    collected_at = datetime.now(timezone.utc)

    for endpoint in endpoints:
        if endpoint.startswith("/wmapp"):
            url = base_url.replace("/wmapp", "") + endpoint
        else:
            url = base_url + endpoint

        try:
            logger.info("Probing XHR fallback endpoint: %s", url)
            response = await client.get(url)
            if response.status_code == 200:
                try:
                    data = response.json()
                    if isinstance(data, list):
                        records = []
                        for index, item in enumerate(data):
                            records.append(
                                {
                                    "card_no": str(item.get("card_no", index + 1)),
                                    "turbine_name": item.get("turbine_name", f"WT-{index + 1:02d}"),
                                    "status": str(item.get("status", "UNKNOWN")).upper(),
                                    "today_kwh": Decimal(str(item["today_kwh"])) if item.get("today_kwh") is not None else None,
                                    "wind_speed": Decimal(str(item["wind_speed"])) if item.get("wind_speed") is not None else None,
                                    "kw": Decimal(str(item["kw"])) if item.get("kw") is not None else None,
                                    "turbine_datetime": datetime.fromisoformat(item["turbine_datetime"]) if item.get("turbine_datetime") is not None else None,
                                    "raw_text": item.get("raw_text", "XHR fallback parsed"),
                                    "collected_at": collected_at,
                                }
                            )
                        logger.info("Successfully parsed %d turbine records from XHR fallback.", len(records))
                        return records
                except Exception as parse_exc:
                    logger.debug("Failed parsing JSON from XHR fallback %s: %s", url, parse_exc)
        except Exception as conn_exc:
            logger.debug("XHR fallback connection failed for %s: %s", url, conn_exc)

    return []


async def _mark_all_unknown() -> None:
    """
    Called after 3 consecutive collection failures.
    Inserts UNKNOWN records for all previously seen turbines.
    """
    async with async_session_factory() as session:
        try:
            result = await session.exec(
                select(TurbineTelemetry.turbine_name)
                .distinct()
                .where(TurbineTelemetry.turbine_name.isnot(None))
            )
            turbine_names = result.all()

            now = datetime.now(timezone.utc)
            for name in turbine_names:
                record = TurbineTelemetry(
                    turbine_name=name,
                    status="UNKNOWN",
                    collected_at=now,
                    raw_text="Ingestion failure — status unknown",
                )
                session.add(record)

            await session.commit()
            logger.warning(
                "Marked %d turbines as UNKNOWN due to consecutive ingestion failures.",
                len(turbine_names),
            )
        except Exception as exc:
            await session.rollback()
            logger.error("Failed to mark turbines UNKNOWN: %s", exc)


async def collection_cycle() -> None:
    """
    Single ingestion cycle — called by APScheduler every 15 minutes.

    1. Fetch dashboard HTML (renew session if needed)
    2. Parse turbine cards
    3. Save to turbine_telemetry
    4. Run alert state machine
    """
    global _client, _consecutive_failures

    logger.info("Starting collection cycle.")

    try:
        if _client is None:
            _client = _make_client()
            if not await login(_client):
                _consecutive_failures += 1
                logger.error(
                    "Login failed. Consecutive failures: %d", _consecutive_failures
                )
                _client = None  # Reset client to force fresh login next cycle!
                if _consecutive_failures >= _MAX_CONSECUTIVE_FAILURES:
                    await _mark_all_unknown()
                return

        # Fetch dashboard
        dashboard_response = await _client.get(ROOKTEC_DASHBOARD_URL)

        # Detect session expiry (redirect back to login page)
        if "login" in str(dashboard_response.url).lower():
            logger.warning("Session expired — attempting re-login.")
            await _client.aclose()
            _client = _make_client()
            if not await login(_client):
                _consecutive_failures += 1
                logger.error(
                    "Re-login failed. Consecutive failures: %d", _consecutive_failures
                )
                _client = None  # Reset client to force fresh login next cycle!
                if _consecutive_failures >= _MAX_CONSECUTIVE_FAILURES:
                    await _mark_all_unknown()
                return
            # Retry the dashboard fetch once
            dashboard_response = await _client.get(ROOKTEC_DASHBOARD_URL)

        records = _parse_turbine_cards(dashboard_response.text)

        if not records:
            logger.warning("No turbine cards parsed from HTML. Triggering XHR fallback probe.")
            records = await _probe_xhr_endpoints(_client)

        if not records:
            _consecutive_failures += 1
            logger.warning(
                "No cards parsed. Consecutive failures: %d", _consecutive_failures
            )
            if _consecutive_failures >= _MAX_CONSECUTIVE_FAILURES:
                await _mark_all_unknown()
            return

        # Successful parse — reset failure counter
        _consecutive_failures = 0

        # Run telemetry insertion and alert state machine in a single, unified database session scope
        async with async_session_factory() as session:
            try:
                saved_records: list[TurbineTelemetry] = []
                for rec in records:
                    obj = TurbineTelemetry(**rec)
                    session.add(obj)
                    saved_records.append(obj)
                
                # Flush to database so the records are populated with auto-generated primary keys,
                # keeping them attached and active in this transactional context.
                await session.flush()
                logger.info("Saved %d telemetry records to DB (flushed).", len(saved_records))

                # Pass attached entities to the alert state machine in the exact same transaction context
                await run_alert_state_machine(session, saved_records)
                
                await session.commit()
                logger.info("Telemetry storage and alert state machine completed successfully.")
            except Exception as db_exc:
                await session.rollback()
                logger.error("Failed to commit database transaction in collection cycle: %s", db_exc)
                raise

    except httpx.HTTPError as exc:
        _consecutive_failures += 1
        logger.error(
            "HTTP error during collection cycle: %s. Consecutive failures: %d",
            exc,
            _consecutive_failures,
        )
        if _consecutive_failures >= _MAX_CONSECUTIVE_FAILURES:
            await _mark_all_unknown()
        # Reset client to force fresh login on next cycle
        if _client:
            await _client.aclose()
            _client = None

    except Exception as exc:
        _consecutive_failures += 1
        logger.error(
            "Unexpected error in collection cycle: %s. Consecutive failures: %d",
            exc,
            _consecutive_failures,
        )
        if _consecutive_failures >= _MAX_CONSECUTIVE_FAILURES:
            await _mark_all_unknown()


def create_scheduler() -> AsyncIOScheduler:
    """
    Build and return the APScheduler instance.
    The scheduler is started by the FastAPI lifespan handler.
    """
    scheduler = AsyncIOScheduler(timezone="UTC")
    scheduler.add_job(
        collection_cycle,
        trigger="interval",
        minutes=COLLECTION_INTERVAL_MINUTES,
        next_run_time=datetime.now(timezone.utc),  # Trigger immediate live scrape on startup!
        id="turbine_collection",
        replace_existing=True,
        max_instances=1,
        coalesce=True,
    )
    return scheduler


# ---------------------------------------------------------------------------
# Standalone entry point (for testing the collector in isolation)
# ---------------------------------------------------------------------------

if __name__ == "__main__":

    async def _main() -> None:
        await collection_cycle()

    asyncio.run(_main())

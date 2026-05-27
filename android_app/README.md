# AeroMonitor — Android App

A native Android app (Kotlin + Jetpack Compose) for the AeroMonitor v2.4 wind farm monitoring platform.

## Features

- **Dashboard**: Live turbine grid with glassmorphism cards, farm KPIs (total/running/failed/kWh), pull-to-refresh
- **Alerts**: Blocking critical alert modal with Acknowledge & Snooze (15/30/60m), multi-alert queue navigation
- **History**: Paginated alert history with severity/state filters and stats pods
- **Settings**: Backend URL, Rooktec credentials, manual scrape trigger, connection test

## Architecture

```
Rooktec Portal ──(OkHttp + Jsoup)──▶ RooktecScraper.kt ──▶ TelemetryRepository
                                                                     │
FastAPI Backend ──(Retrofit)──────▶ AeroMonitorApi.kt ──▶ AlertRepository
                                                                     │
                                                              ViewModels (MVVM)
                                                                     │
                                                         Jetpack Compose Screens
```

## Setup

### Prerequisites
1. **Android Studio** (Hedgehog or newer) — [Download](https://developer.android.com/studio)
2. **JDK 17** — bundled with Android Studio
3. The AeroMonitor Docker backend running on your PC

### Build & Run

```bash
# Open android_app/ folder in Android Studio
# Click Run ▶ on an emulator or device

# Or from command line (after Android Studio sets up the SDK):
cd android_app
./gradlew assembleDebug
```

### Configure Backend URL

- **Emulator**: Use default `http://10.0.2.2/api` (Android's alias for the host machine)
- **Real device**: Open Settings tab in the app → enter `http://<your-PC-LAN-IP>/api`

Get your PC's IP: `ipconfig | findstr IPv4`

### Configure Rooktec Credentials

In the app's Settings tab:
- **Portal URL**: `https://www.rooktec.in/wmapp`
- **Username**: `smb`
- **Password**: `wind@smb`

Tap **Scrape Now** to test connectivity immediately.

## Project Structure

```
android_app/
├── app/src/main/
│   ├── java/com/aeromdc/aeromonitor/
│   │   ├── MainActivity.kt              # App entry + navigation
│   │   ├── data/
│   │   │   ├── model/Models.kt          # All data classes
│   │   │   ├── network/
│   │   │   │   ├── AeroMonitorApi.kt    # Retrofit API interface
│   │   │   │   ├── RooktecScraper.kt    # OkHttp + Jsoup scraper
│   │   │   │   └── InMemoryCookieJar.kt # Session cookie management
│   │   │   └── repository/
│   │   │       └── AeroMonitorRepository.kt
│   │   ├── ui/
│   │   │   ├── dashboard/               # Dashboard screen + ViewModel
│   │   │   ├── alerts/                  # Alert modal screen + ViewModel
│   │   │   ├── history/                 # History list + ViewModel
│   │   │   ├── settings/                # Settings screen
│   │   │   └── theme/Theme.kt           # Material 3 dark color scheme
│   │   └── util/AppPreferences.kt       # DataStore preferences
│   ├── res/values/
│   │   ├── strings.xml
│   │   └── themes.xml
│   └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml            # All dependency versions
```

## API Endpoints Used

| Method | Endpoint | Used By |
|--------|----------|---------|
| `GET` | `/api/telemetry/live` | Dashboard (fallback) |
| `GET` | `/api/alerts/active` | Dashboard + Alerts |
| `POST` | `/api/alerts/{id}/acknowledge` | Alerts screen |
| `POST` | `/api/alerts/{id}/snooze` | Alerts screen |
| `GET` | `/api/alerts/history` | History screen |

> **Note:** Live telemetry is scraped directly from Rooktec by the app itself (via `RooktecScraper.kt`).
> The backend `/api/telemetry/live` is used only as a fallback if the scrape fails.

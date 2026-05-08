# Drone Flight Analytics — Project Handoff

A full-stack SaaS portfolio project: upload drone flight logs, get instant path
efficiency, battery regression, anomaly detection, and airspace risk reports.

---

## Architecture Overview

```
drone-ingestion/          Java 17 / Spring Boot 3.2 backend
├── ingestion/            5-format log parser (DJI, ArduPilot, PX4, MAVLink, Parrot)
├── analytics/engine/     Statistics, path optimisation, battery regression,
│                         anomaly detection, airspace risk scoring
├── reporting/            PDF (PDFBox) + CSV (Commons CSV) export
├── notification/         In-app + optional webhook alerts
├── auth/                 JWT (JJWT 0.12), BCrypt, Spring Security 6
├── entity/               JPA entities: User, FlightSession
├── repository/           Spring Data JPA repositories
├── service/              FlightSessionService (history CRUD)
└── api/controller/       FlightController + AuthController

drone-dashboard/          React 18 + Vite frontend
├── src/api/              authApi.js, flightApi.js  (JWT-aware fetch wrappers)
├── src/components/
│   ├── AuthPage.jsx      Login / register tabs
│   ├── UploadPanel.jsx   Drag-and-drop file upload
│   ├── FlightDashboard.jsx  Full report view (map + charts + anomalies)
│   ├── FlightHistory.jsx    Paginated past-sessions sidebar
│   └── FlightMap.jsx / FlightCharts.jsx / AnomalyPanel.jsx
└── src/App.jsx           Root state machine (guest → upload → loading → dashboard)

.github/workflows/ci.yml  GitHub Actions CI (Java tests + Vite build + Docker build)
docker-compose.yml        Production stack: PostgreSQL + backend + Nginx frontend
```

---

## Steps Completed

| # | What was built |
|---|---|
| 1 | Java ingestion pipeline — 5 drone log formats → `TelemetryPoint` |
| 2 | Analytics engine — statistics, path optimisation, battery regression, anomaly detection, airspace risk |
| 3 | REST API — Spring Boot, `FlightController`, CSV/PDF export |
| 4 | Notification service — in-app alerts + optional webhook |
| 5 | React dashboard — Leaflet map (altitude-coloured flight path), Chart.js time-series |
| 6 | Anomaly & airspace panels, export buttons (CSV + PDF) |
| 7 | JWT authentication — register/login, `JwtAuthFilter`, Spring Security 6 |
| 8 | JPA persistence — H2 dev / PostgreSQL prod, Flyway migrations, flight history API |
| 9 | Dockerisation — multi-stage Dockerfiles, docker-compose, GitHub Actions CI |

---

## Running Locally (development)

### Prerequisites
- Java 17, Maven 3.9+
- Node 20+

### Backend
```bash
cd drone-ingestion
mvn spring-boot:run
# API at http://localhost:8080
# H2 data persisted in ./data/drone-analytics.mv.db
```

### Frontend
```bash
cd drone-dashboard
npm install
npm run dev
# Opens http://localhost:5173
# Vite proxies /api/* → localhost:8080
```

### Tests
```bash
# Backend — 37 tests (7 ingestion + 7 analytics + 13 FlightController + 10 AuthController)
cd drone-ingestion && mvn test

# Frontend build check
cd drone-dashboard && npm run build
```

---

## Running with Docker (production stack)

### Prerequisites
- Docker 24+ with Compose V2 (`docker compose` not `docker-compose`)

### Steps

```bash
# 1. Copy and fill in secrets
cp .env.example .env
# Edit .env: set DB_PASSWORD and JWT_SECRET (see below)

# 2. Build images and start all services
docker compose up --build

# 3. Open http://localhost
```

Services:

| Service | Description | Port |
|---|---|---|
| `db` | PostgreSQL 16 | internal only |
| `backend` | Spring Boot API | internal (health-checked) |
| `frontend` | Nginx — React SPA + /api proxy | **80** |

### Useful commands

```bash
docker compose logs -f backend      # stream backend logs
docker compose down                 # stop (keeps volumes / data)
docker compose down -v              # stop AND delete all data
docker compose up --build backend   # rebuild just the backend
```

### Health check
```bash
curl http://localhost/api/health
# Drone Analytics API is running
```

---

## Environment Variables

Copy `.env.example` → `.env` and fill in the required values.

### Required

| Variable | Description |
|---|---|
| `DB_PASSWORD` | PostgreSQL password — use something strong |
| `JWT_SECRET` | JWT signing secret — must be **≥ 32 characters**. Generate: `openssl rand -base64 48` |

### Optional

| Variable | Default | Description |
|---|---|---|
| `JWT_EXPIRATION_SECONDS` | `86400` | Token lifetime (24 h) |
| `FRONTEND_PORT` | `80` | Host port for the web UI |
| `WEBHOOK_ENABLED` | `false` | Enable POST webhook on anomaly/risk alerts |
| `WEBHOOK_URL` | _(empty)_ | Webhook endpoint URL |

---

## Database

### Development (H2)
File-based H2 at `drone-ingestion/data/drone-analytics.mv.db`.  
Flyway creates the schema automatically on first run.

If you modify `V1__create_tables.sql` during development, delete the data
directory so Flyway can re-apply it:
```bash
rm -rf drone-ingestion/data/
```

### Production (PostgreSQL 16)
The `prod` Spring profile uses `application-prod.properties` which points Flyway
at `classpath:db/postgresql/V1__create_tables.sql` — a PostgreSQL-native
version of the schema (`TEXT` instead of `CLOB`, `DOUBLE PRECISION` instead of `DOUBLE`).

Flyway runs automatically on startup. Subsequent boots are no-ops.

---

## CI / CD

`.github/workflows/ci.yml` triggers on every push and pull request:

1. **Backend** — `mvn verify` — compiles and runs all 37 tests (H2 in-memory)
2. **Frontend** — `npm ci` + `npm run build` — full Vite production build
3. **Docker** — builds both images (no push) — catches Dockerfile regressions, uses GHA layer cache

To publish images, add your registry credentials to GitHub Secrets and extend
the `docker` job with `docker/login-action` and `push: true`.

---

## API Reference

### Auth (no token required)

| Method | Path | Body | Response |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{email, password, displayName}` | `{token, email, displayName, expiresInSeconds}` |
| `POST` | `/api/auth/login` | `{email, password}` | same |
| `GET` | `/api/auth/me` | — | `{email, displayName}` |

### Flights (`Authorization: Bearer <token>` required)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/flights/analyze` | Upload log file → full JSON analysis |
| `POST` | `/api/flights/report/csv` | Upload log file → CSV download |
| `POST` | `/api/flights/report/pdf` | Upload log file → PDF download |
| `GET` | `/api/flights?page=0&size=20` | Paginated flight history |
| `GET` | `/api/flights/{id}` | Full report for a past session |
| `DELETE` | `/api/flights/{id}` | Delete a past session |

### Health (public)

| Method | Path | |
|---|---|---|
| `GET` | `/api/health` | Liveness probe — returns 200 |

---

## Supported Log Formats

| Manufacturer | Extension | Detection method |
|---|---|---|
| DJI | `.txt` | 100-byte OSD binary header |
| ArduPilot | `.bin` | DataFlash magic `0xA3 0x95` |
| PX4 | `.ulg` | ULog magic `ULog\x01\x12\x35` |
| MAVLink TLOG | `.tlog` | 8-byte µs timestamp + `0xFE`/`0xFD` |
| Parrot | `.json` | JSON root with `"datas"` or `"product"` key |

---

## Key File Map

```
claude project 1/
├── .env.example                            ← copy to .env, fill secrets
├── .gitignore                              ← excludes .env, data/, target/, node_modules/
├── .github/workflows/ci.yml               ← GitHub Actions CI
├── docker-compose.yml                     ← production stack
├── HANDOFF.md                             ← this file
│
├── drone-ingestion/                       ← Java 17 / Spring Boot backend
│   ├── Dockerfile                         ← multi-stage Maven + JRE 17 Alpine
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/droneanalytics/
│       │   ├── auth/                      ← JwtUtils, JwtAuthFilter, AuthController
│       │   ├── api/config/                ← SecurityConfig, CorsConfig
│       │   ├── api/controller/            ← FlightController
│       │   ├── api/dto/                   ← AnalysisReportDto, FlightSessionDto, AuthDto
│       │   ├── entity/                    ← User, FlightSession (@Entity)
│       │   ├── repository/                ← UserRepository, FlightSessionRepository
│       │   ├── service/                   ← FlightSessionService
│       │   ├── analytics/engine/          ← AnalyticsEngine + 5 sub-engines
│       │   ├── ingestion/                 ← FlightLogIngestionService + 5 parsers
│       │   ├── reporting/                 ← ReportExporter (PDF + CSV)
│       │   └── notification/              ← NotificationService
│       └── resources/
│           ├── application.properties     ← dev profile (H2 file)
│           ├── application-prod.properties← prod profile (PostgreSQL, env vars)
│           ├── db/migration/
│           │   └── V1__create_tables.sql  ← H2 schema (dev + test)
│           └── db/postgresql/
│               └── V1__create_tables.sql  ← PostgreSQL schema (prod)
│
└── drone-dashboard/                       ← React 18 + Vite frontend
    ├── Dockerfile                         ← Node 20 build + Nginx Alpine serve
    ├── nginx.conf                         ← SPA routing + /api proxy + security headers
    ├── vite.config.js                     ← dev proxy to :8080
    └── src/
        ├── App.jsx / App.css
        ├── api/authApi.js
        ├── api/flightApi.js
        └── components/
            ├── AuthPage.jsx
            ├── UploadPanel.jsx
            ├── FlightDashboard.jsx
            ├── FlightHistory.jsx
            ├── FlightMap.jsx
            ├── FlightCharts.jsx
            └── AnomalyPanel.jsx
```

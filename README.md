# Drone Flight Analytics

A full-stack SaaS portfolio project. Upload a drone flight log and get an instant report: flight path map, battery regression, anomaly detection, path efficiency score, and airspace risk analysis. Accounts are persistent — flight history is saved and accessible on any device.

**Live demo:** [drone.brsoftware.ca](https://drone.brsoftware.ca)

---

## What it does

1. **Upload** a drone log file (drag and drop)
2. **Analyse** — the backend parses the telemetry, runs 5 analytics engines, and returns a full report in under a second
3. **Explore** — interactive Leaflet map with altitude-coloured flight path, Chart.js time-series graphs, anomaly timeline, airspace risk breakdown
4. **Export** — download the report as CSV or PDF
5. **History** — all past flights are saved to your account and reloadable at any time

### Supported log formats

| Manufacturer | File type | Detection |
|---|---|---|
| DJI | `.txt` | 100-byte OSD binary header |
| ArduPilot | `.bin` | DataFlash magic bytes `0xA3 0x95` |
| PX4 | `.ulg` | ULog magic `ULog\x01\x12\x35` |
| MAVLink | `.tlog` | 8-byte µs timestamp + MAVLink start byte |
| Parrot | `.json` | JSON root with `"datas"` or `"product"` key |

---

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite 5, Leaflet, Chart.js |
| Backend | Java 17, Spring Boot 3.2, Spring Security 6, JWT |
| Database | H2 (dev) / PostgreSQL 16 (prod) |
| Migrations | Flyway |
| Infrastructure | Docker, Docker Compose, AWS (S3 + CloudFront + Elastic Beanstalk + RDS) |
| CI/CD | GitHub Actions |

---

## Running locally (no Docker)

### Prerequisites
- Java 17+, Maven 3.9+
- Node 20+

### 1. Start the backend
```bash
cd drone-ingestion
mvn spring-boot:run
```
API runs at `http://localhost:8080`. Uses an H2 file database — no setup needed. Data is saved to `drone-ingestion/data/drone-analytics.mv.db`.

### 2. Start the frontend
```bash
cd drone-dashboard
npm install
npm run dev
```
Opens at `http://localhost:5173`. Vite proxies all `/api/*` requests to the backend automatically.

### 3. Run tests
```bash
# Backend — 37 tests
cd drone-ingestion && mvn test

# Frontend build check
cd drone-dashboard && npm run build
```

---

## Running with Docker

The Docker setup runs the full production stack: PostgreSQL + Spring Boot + Nginx serving the React app.

### Prerequisites
- Docker 24+ with Compose V2

### Steps

```bash
# 1. Copy the example env file and fill in the two required secrets
cp .env.example .env
```

Open `.env` and set:
- `DB_PASSWORD` — any strong password
- `JWT_SECRET` — random string, at least 32 characters (generate one with `openssl rand -base64 48`)

```bash
# 2. Build and start everything
docker compose up --build

# 3. Open the app
# http://localhost
```

### Verify it's running
```bash
curl http://localhost/api/health
# Drone Analytics API is running
```

### Useful commands
```bash
docker compose logs -f backend       # stream backend logs
docker compose down                  # stop (data is kept)
docker compose down -v               # stop and delete all data
docker compose up --build backend    # rebuild just the backend after code changes
```

---

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_PASSWORD` | Yes | — | PostgreSQL password |
| `JWT_SECRET` | Yes | — | JWT signing secret, min 32 chars |
| `JWT_EXPIRATION_SECONDS` | No | `86400` | Token lifetime (24 h) |
| `FRONTEND_PORT` | No | `80` | Host port for the web UI |
| `WEBHOOK_ENABLED` | No | `false` | Send POST alerts on anomalies/risk events |
| `WEBHOOK_URL` | No | — | Webhook endpoint (e.g. Slack incoming webhook) |

---

## API reference

### Auth (no token required)
| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{email, password, displayName}` | `{token, email, displayName, expiresInSeconds}` |
| `POST` | `/api/auth/login` | `{email, password}` | same |
| `GET` | `/api/auth/me` | — | `{email, displayName}` |

### Flights (requires `Authorization: Bearer <token>`)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/flights/analyze` | Upload log file → full JSON analysis report |
| `POST` | `/api/flights/report/csv` | Upload log file → CSV download |
| `POST` | `/api/flights/report/pdf` | Upload log file → PDF download |
| `GET` | `/api/flights?page=0&size=20` | Paginated flight history |
| `GET` | `/api/flights/{id}` | Full report for a saved session |
| `DELETE` | `/api/flights/{id}` | Delete a saved session |

### Health
| Method | Path | |
|---|---|---|
| `GET` | `/api/health` | Liveness probe — always returns 200 |

---

## Project structure

```
.
├── drone-ingestion/            Java 17 / Spring Boot backend
│   ├── src/main/java/
│   │   └── com/droneanalytics/
│   │       ├── auth/           JWT filter, auth controller, BCrypt
│   │       ├── api/            FlightController, DTOs, CORS + security config
│   │       ├── entity/         User, FlightSession (JPA)
│   │       ├── service/        FlightSessionService (history CRUD)
│   │       ├── ingestion/      Log parsers (DJI, ArduPilot, PX4, MAVLink, Parrot)
│   │       ├── analytics/      Statistics, path optimisation, battery regression,
│   │       │                   anomaly detection, airspace risk scoring
│   │       ├── reporting/      PDF (PDFBox) + CSV (Commons CSV) export
│   │       └── notification/   In-app + webhook alerts
│   └── src/main/resources/
│       ├── application.properties          Dev profile (H2)
│       ├── application-prod.properties     Prod profile (PostgreSQL, env vars)
│       ├── db/migration/V1__create_tables.sql      H2 schema
│       └── db/postgresql/V1__create_tables.sql     PostgreSQL schema
│
├── drone-dashboard/            React 18 + Vite frontend
│   └── src/
│       ├── api/                authApi.js, flightApi.js (JWT-aware fetch)
│       └── components/
│           ├── AuthPage.jsx        Login / register
│           ├── UploadPanel.jsx     Drag-and-drop upload
│           ├── FlightDashboard.jsx Full report view
│           ├── FlightHistory.jsx   Past sessions sidebar
│           ├── FlightMap.jsx       Leaflet map
│           ├── ChartsPanel.jsx     Chart.js time-series
│           └── AnomalyPanel.jsx    Anomaly + risk breakdown
│
├── docker-compose.yml          Full stack: PostgreSQL + backend + Nginx frontend
├── .env.example                Copy to .env and fill in secrets
├── .github/workflows/ci.yml    CI: Java tests + Vite build + Docker build
└── aws-deploy/DEPLOY_GUIDE.md  Step-by-step AWS deployment guide
```

---

## Deploying to AWS

The project includes a complete guide for deploying to AWS free tier (S3 + CloudFront for the frontend, Elastic Beanstalk for the backend, RDS PostgreSQL for the database). Expected cost: **$0/month for the first 12 months**.

See [`aws-deploy/DEPLOY_GUIDE.md`](aws-deploy/DEPLOY_GUIDE.md) for step-by-step instructions.

Deployment is automated via GitHub Actions — once secrets are configured, every push to `main` deploys both frontend and backend automatically.

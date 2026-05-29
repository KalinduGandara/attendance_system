# Attendance System

A web-based Time & Attendance platform inspired by BioStar 2 T&A. Single-tenant, self-hosted, with a pluggable ingestion layer so punch events can come from REST, future device SDKs, external databases, or file imports.

## Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Maven |
| Persistence | JPA / Hibernate, Flyway, MariaDB (extensible to PostgreSQL / MSSQL) |
| Security | Spring Security + JWT (access + refresh) |
| Frontend | React 18, TypeScript, Vite, Mantine, TanStack Query, React Router |
| Tests | JUnit 5 + Testcontainers (backend), Vitest + Playwright (frontend) |
| Deployment | Docker Compose |

## Repository Layout

```
attendance_system2/
├── backend/            Spring Boot application (Maven)
├── frontend/           React + Vite application
├── docs/               Architecture, plan, ADRs, data model, API, runbook
├── docker-compose.yml  Local dev stack (app + MariaDB)
└── srs.md              Source SRS document
```

## Documentation Map

Read in this order:

1. [docs/architecture.md](docs/architecture.md) — system design, modules, key abstractions
2. [docs/data-model.md](docs/data-model.md) — entities and relationships
3. [docs/api-contracts.md](docs/api-contracts.md) — REST API surface
4. [docs/plan.md](docs/plan.md) — phased implementation plan
5. [docs/runbook.md](docs/runbook.md) — local setup, backups, restore
6. [docs/adr/](docs/adr/) — Architecture Decision Records

## Quick start (dev)

```
cp .env.example .env
docker compose up --build       # mariadb :3306, backend :3000, frontend :8080
```

Bootstrap credentials (rotate immediately):

| username | password      |
|----------|---------------|
| `admin`  | `Admin@12345` |

Browse:
- Frontend: http://localhost:8080
- Backend API: http://localhost:3000/api/v1/health
- Swagger UI: http://localhost:3000/swagger-ui

Without Docker:

```
# backend
cd backend && mvn spring-boot:run         # needs MariaDB on :3306

# frontend
cd frontend && npm install && npm run dev # http://localhost:5173, proxies /api to :3000
```

## Status

**Phase 8 (Reporting) complete.** Adds:

- Table (V10 migration): `report_job` — tracks asynchronous report generation; all state
  (type, parameters JSON, status, file path, row count) is persisted so a job survives a
  backend restart.
- `ReportExporterPort` + `CsvExporter` (RFC 4180, CRLF, no BOM) writing to the `reports/`
  directory; one `ReportBuilder` per SRS §3.6 report type: Daily, Daily Summary, Individual,
  Individual Summary, Leave, Exception, Modified Punch Log History.
- REST `POST /reports` (202 + queued job), `GET /reports` (recent jobs), `GET /reports/{id}`
  (status + download URL when `DONE`), `GET /reports/{id}/download` (streams the CSV). All
  gated by `report.run`. Generation runs on a dedicated `@Async("reportExecutor")` pool.
- Reports support an employee / department / group scope, a date window, a status filter
  (Leave & Exception), selectable employee custom-field columns, and best-effort sort overrides.
- Frontend Reports page: pick type → parameter form → run → poll status → download, plus a
  recent-jobs table and per-user saved parameter presets (localStorage; Phase 9 promotes to
  org-wide).
- Tests (245 passing total, +13 in Phase 8): a golden-fixture suite comparing each of the seven
  reports byte-for-byte against a committed CSV, plus controller security gates.

**Phase 2 (Devices, Credentials, Ingestion Sources) complete.** Adds:

- Tables (V5 migration): `device`, `ingestion_source`, `device_ingestion_source`, `credential`.
  Seeds one REST source ("Default Web Ingestion") with no API key — the admin must rotate-key
  on it to start accepting `X-Source-Api-Key` traffic.
- REST CRUD for `/devices` (`device.read|write`), `/ingestion-sources` (`device.read|write`),
  and `/employees/{id}/credentials` (`employee.read|write`). API keys for sources are generated
  on create / rotate, returned plaintext exactly once, persisted only as SHA-256.
- Credential resolution service: given `(type, rawValue)` returns the active credential's
  employee id, gated by `valid_from`/`valid_to` and `status=ACTIVE`. Phase 5 ingest hooks here.
- `X-Source-Api-Key` authentication filter — runs before the JWT filter, populates a distinct
  `IngestionSourcePrincipal` granting only `ingestion.write`. A stub `GET /ingestion/ping`
  endpoint exercises the wiring (real `POST /ingestion/punches` lands in Phase 5).
- Frontend pages: Devices (paged list + status filter + capabilities JSON), Ingestion sources
  (list + rotate-key flow that reveals the plaintext key once in a copyable dialog) and a
  Credentials card on each employee form for issue/revoke/delete.
- Tests (57 passing total, +28 in Phase 2): device CRUD + paged search, ingestion source
  create/rotate/disable + API key lookup, credential uniqueness across types + revoke flow +
  validity-window resolution, end-to-end API key auth via MockMvc, controller security gates.

**Phase 1 (Identity & Organization) complete.** Adds:

- Tables (V3 + V4 migrations): `department`, `user_group`, `employee`, `employee_group`,
  `custom_field_definition`, `custom_field_value`, `holiday`, `holiday_group`,
  `employee_import_job`.
- REST CRUD for users, roles (read), permissions (read), employees, departments (with tree),
  groups (with tree), custom fields, holidays. All write endpoints permission-gated and audited.
- Manager-scope service: given a user, returns the set of visible employee ids (admin/HR sees all;
  managers see self + transitive direct reports). Foundation for Phase 4+ row-level filters.
- Bulk employee CSV import — synchronous below 1000 rows, async (`@Async`) above. Job status
  persisted in `employee_import_job`.
- Frontend pages (React Hook Form + Zod, permission-gated): Users, Employees list & form
  (with custom-field inputs), Departments tree, Groups tree, Custom Fields, Holidays.
- Tests (28 passing): department / group hierarchy + cycle detection, employee CRUD + custom
  fields, manager scope resolution, CSV import (sync + error reporting), controller security.

**Phase 0 (Foundation) complete.** End-to-end auth slice, Flyway-managed schema, audit
infrastructure, OpenAPI/Swagger, Mantine app shell, dev docker-compose, GitHub Actions CI.

Next: Phase 3 — Time Codes & Shifts (see [docs/plan.md](docs/plan.md)).

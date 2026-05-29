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

**Phase 9 (System Admin) complete.** Adds:

- Tables (V11 migration): `system_setting` (type-aware org config), `backup_job` (tracks each
  database dump; a job-tracking table like `report_job`), `retention_policy` (per-entity purge
  window). Permissions `audit.read` and `system.admin` were already seeded in V2.
- Audit viewer: `GET /audit-events` (paged, filter by actor / action / entity / date) + `GET
  /audit-events/{id}` for the before/after drilldown, gated by `audit.read`. The audit table is
  owned by the shared kernel; the admin module queries it without crossing module boundaries.
- System settings: `GET` + `PATCH /system/settings` (`system.admin`) with type-aware validation
  (`NUMBER`/`BOOLEAN`/`JSON`, and `*_cron` keys checked against Spring cron). Every change is audited.
- Scheduled backups: `mysqldump` via a new `DatabaseDialectPort` (ADR-0003) → `MariaDbDialect`,
  written under `backups/` on the `backupExecutor` pool; `POST /system/backups` runs one now, with
  list / status / download endpoints. The cron lives in `system_setting` (`backup_cron`,
  `backup_enabled`) and re-schedules live via a dynamic `Trigger`; successful runs rotate to
  `backup_keep_count`.
- Retention: `retention_policy` per entity (`punch_event`, `audit_event`, `report_job`), purged in
  bounded batches through a per-module `RetentionPort` (no cross-module repository access);
  `punch_event` keeps any punch referenced by a manual edit. `GET` / `PUT
  /system/retention-policies/{entityType}` (`system.admin`); all disabled by default.
- Frontend `features/admin/`: Audit Log (filters + drilldown), System Settings (typed editors),
  Backups (run / poll / download), Retention (per-entity window + enable). Routes + nav gated by
  `audit.read` (audit) and `system.admin` (the rest).
- Tests (269 passing total, +23 in Phase 9): controller security gates, retention purge respects
  the window and never touches recent data (the SRS NFR-3 `punch_event` path), setting type
  validation, backup orchestration with a mocked executor (DONE + size, failure path), the
  `mysqldump` command shape (secret kept out of the argv), and an audit-trail regression that a
  settings change writes an `audit_event`.

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
- Tests (246 passing total, +14 in Phase 8): a golden-fixture suite comparing each of the seven
  reports byte-for-byte against a committed CSV, an async-path test that drives `submit()` through
  the `@Async`/`@Transactional` worker to completion, plus controller security gates.

**Phase 7 (Leaves & Exceptions) complete.** Adds:

- Tables (V9 migration): `leave_type`, `leave_balance`, `leave_request` (`exception_event` already
  shipped in V8 and is now exposed via API). The balance column is `balance_year` because `year`
  is reserved in H2/MySQL; the JPA field stays `year`.
- `LeaveType` CRUD (validates the referenced time code is `LEAVE`-category, name unique);
  balances read/`adjust` plus `deduct`/`refund` that lazy-create the `(employee, type, year)` row
  at the type's default annual days; request lifecycle create/approve/reject/cancel with a
  retroactive flag. Approve debits the balance and runs a synchronous recompute over the range;
  cancel refunds and re-runs; half-day = 0.5 day on a single date.
- Exception API: `GET /exceptions` (filters, `exception.read`) and
  `PATCH /exceptions/{id}/resolve` → `RESOLVED`/`IGNORED` (`exception.resolve`). The per-day
  recompute refreshes open exceptions, so an approved retroactive leave atomically closes the
  matching `ABSENT_NO_LEAVE`.
- Frontend: Leave Types admin, Employee Leave (balance cards + request form + history, consuming
  the Phase-6 retroactive deep-link), Approvals queue (approve/reject-with-reason), Exceptions
  page (status/date filters, bulk-resolve, drilldown to the time card).
- Tests (232 passing total, +16): leave lifecycle — approving leave on an absent day flips the
  card to `LEAVE` and closes the exception; half-day halves the balance and partially fills the
  card; cancel refunds; reject leaves the balance untouched — plus controller security gates.

**Phase 6 (Time Card UI) complete.** Adds:

- Backend: `GET /timecards` (employee / date-range / group / status filters) and
  `POST /timecards/{id}/edits` — manual punch edit with a required reason, synchronous recompute,
  and an append-only `time_card_edit` row. Unresolved-punch read + credential-assignment service.
- Frontend: Time Card dashboard with a Calendar (FullCalendar, days colored by `time_code.color`)
  / List toggle; click-a-day side panel showing raw punches, breakdown, exceptions and edit
  history; edit-punch modal (time picker + required reason); a "Register retroactive leave" entry
  point that deep-links into the Phase-7 leave form; the unresolved-punches queue with credential
  assignment; and a punches list.
- Tests: time-card edit + synchronous recompute, unresolved-punch handling, and controller
  security (late punch → `LATE_IN` → edit clears it; edit without a reason is rejected).

**Phase 5 (Punch Ingestion + Time-Card Engine) complete.** Adds:

- Tables (V8 migration): `punch_event` (with an idempotency unique index), `daily_time_card`,
  `time_card_breakdown`, `time_card_edit`, `exception_event`. JSON payload columns use `TEXT`
  per ADR 0003.
- `POST /ingestion/punches` batch endpoint — JWT (admin/manager) or `X-Source-Api-Key` auth,
  `Idempotency-Key` whole-batch dedup plus per-event `(source, external_event_id)` idempotency;
  credential resolution with unresolved events stored `UNRESOLVED`; publishes `PunchEventIngested`.
- Pure `TimeCardCalculator` with independently tested subroutines (`PunchPairer`, `Rounder`,
  `GraceApplier`, `BreakDeducer`, `OvertimeTierSlicer`, `RatedMinutesCalculator`,
  `ExceptionDetector`); `TimeCardRecomputeService` orchestrates per `(employee, date)`, driven by
  the ingestion event, a nightly Quartz job, and a programmatic API.
- Tests: golden YAML fixture suite (`fixtures/timecard/` — fixed/flex/floating shifts, each
  rounding mode, missing punches, multi-tier OT, cross-midnight…), a jqwik property test, an
  idempotency test, a 1000-event parallel-ingestion concurrency test, and a determinism test.

**Phase 4 (Scheduling & Rostering) complete.** Adds:

- Tables (V7 migration): `schedule_template`, `schedule_template_day`, `schedule_assignment`,
  `temporary_schedule`. V7 also adds the missing `schedule.read` grant for `EMPLOYEE` so staff
  can see their own roster.
- `ScheduleResolver` — a pure, fully-tested `resolveShift(employee, date)` honoring the priority
  `temporary > employee-assignment > group-assignment > none`. CRUD APIs for templates,
  assignments and temporary overrides.
- Frontend: Schedule Templates (drag-and-drop week builder), Assignments (employee/group target
  picker + template + date range), and per-employee Temporary Schedules with a shift override.
- Tests (+38): resolver priority / holidays / edge dates / day-index, plus template, assignment,
  temporary-schedule services and controller security.

**Phase 3 (Time Codes & Shifts) complete.** Adds:

- Tables (V6 migration): `time_code`, `shift`, `shift_segment`, `shift_rounding_rule`,
  `shift_grace_rule`, `break_rule`, `overtime_rule`, `floating_shift_candidate`. Shift children
  are managed as atomic full-replacement collections with `ON DELETE CASCADE`.
- Validations: time-code rate ∈ [0, 10], strictly-increasing OT tier sequence, floating
  candidates must be `FIXED`/`FLEXIBLE`. Caffeine caching of time codes + shifts; APIs return and
  atomically replace nested rules, bumping the optimistic-lock version on update.
- Frontend: Time Codes page with a color picker; Shift editor with Fixed/Flex/Floating type tabs,
  break sub-forms and an OT-tier table; a visual schedule preview from the segments.
- Tests (+23): shift validators per shift type, time-code service, and controller security.

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

Next: Phase 10 — Hardening (perf, security review, accessibility, ops docs) (see [docs/plan.md](docs/plan.md)).

# System Architecture

> Authoritative reference for the design of the Attendance System. All implementation decisions defer to this document. If reality diverges from this doc, update the doc first.

## 1. Goals & Non-Goals

### Goals
- Clone the core functional surface of BioStar 2 T&A (see [../srs.md](../srs.md)).
- Single-tenant, self-hosted, on-premise-friendly.
- Clean module boundaries so functional areas can evolve independently.
- A pluggable ingestion layer so punch events can enter from any source.
- Portable persistence — MariaDB now, swappable to PostgreSQL / MSSQL later.
- Deterministic, auditable time-card computation.

### Non-Goals (for v1)
- Multi-tenant SaaS isolation.
- Real BioStar 2 device SDK integration (handled via future ingestion adapter).
- Native mobile apps.
- Payroll calculation beyond CSV export.
- High-availability clustering (single-node deployment is fine).

## 2. Architectural Style

**Modular monolith with hexagonal seams.**

- One deployable backend, one frontend SPA. Operationally simple.
- Each functional area is a top-level Java package with its own controllers, services, domain, repositories. No cross-module repository or entity access; modules talk through service interfaces or events.
- For the parts we know will evolve, we use **ports & adapters** — explicit interfaces with swappable implementations:
  - `PunchEventIngestionPort` — how punches enter
  - `DatabaseDialectPort` — vendor-specific SQL quirks
  - `ReportExporterPort` — CSV now, PDF/XLSX later
  - `NotificationPort` — email/SMS notifications (future)
- If a module ever needs to scale independently, the seam is already there.

## 3. Backend Module Map

```
com.attendance
├── identity         users, roles, permissions, JWT auth, password policy
├── organization     employees, groups, departments, custom fields, holidays
├── device           logical devices, credentials (RFID/QR/mobile/biometric refs)
├── ingestion        pluggable PunchEventIngestionPort + REST adapter
├── timecode         Attendance / Overtime / Leave codes with rates & colors
├── shift            Fixed / Flexible / Floating shifts, breaks, rounding, OT tiers
├── schedule         templates, assignments, temporary overrides
├── timecard         punch events (raw), daily timecard (computed), edits
├── leave            leave types, balances, requests (incl. retroactive)
├── exception        detection rules, resolution workflow
├── report           7 standard reports, async generation, CSV export
├── admin            audit log viewer, scheduled backups, retention, settings
├── common           shared kernel — base classes, errors, types, time utils
└── platform         framework config — security, persistence, web, scheduling
```

Each module has the same internal structure:

```
identity/
├── web/             REST controllers + request/response DTOs
├── service/         application services (use cases)
├── domain/          entities, value objects, domain services
├── repository/      Spring Data JPA repositories
└── IdentityModule.java   module wiring (Configuration)
```

## 4. Key Architectural Decisions

Each is recorded as an ADR in [adr/](adr/). Headlines:

| # | Decision |
|---|---|
| [0001](adr/0001-modular-monolith.md) | Modular monolith with hexagonal seams |
| [0002](adr/0002-jwt-stateless-auth.md) | Stateless JWT auth (access + refresh) |
| [0003](adr/0003-mariadb-with-dialect-abstraction.md) | MariaDB now, dialect abstraction for portability |
| [0004](adr/0004-pluggable-ingestion.md) | `PunchEventIngestionPort` with REST adapter shipping first |
| [0005](adr/0005-uuid-v7-primary-keys.md) | UUID v7 primary keys everywhere |
| [0006](adr/0006-mantine-ui.md) | Mantine for frontend components |
| [0007](adr/0007-time-card-pure-computation.md) | Time-card computation engine is pure / deterministic |

## 5. Three Critical Abstractions

### 5.1 `PunchEventIngestionPort`

```java
public interface PunchEventIngestionPort {
    IngestionResult ingest(IngestionSource source, PunchEventBatch batch);
}
```

- **Idempotency key**: `(source_id, external_event_id)`. Re-submitting the same event is a no-op.
- **First adapter** (Phase 5): `RestIngestionAdapter` exposing `POST /api/v1/ingestion/punches`.
- **Future adapters**: `DeviceSdkAdapter`, `ExternalDbSyncAdapter`, `CsvImportAdapter`.
- **UI**: an "Ingestion Sources" admin page lets operators register new sources, view stats, pause/resume.
- **Flow**:
  1. Adapter receives raw event(s)
  2. Validates schema + idempotency
  3. Resolves `employee` from `credential_value`
  4. Persists `punch_event` (status = RAW)
  5. Publishes domain event `PunchEventIngested`
  6. `TimeCardRecomputeListener` recomputes the affected `daily_time_card`

### 5.2 `DatabaseDialectPort`

```java
public interface DatabaseDialectPort {
    String name();
    String backupFileExtension();
    List<String> buildBackupCommand(BackupTarget target);   // Phase 9: mysqldump for MariaDB
    Map<String, String> backupEnvironment(BackupTarget target);
    // ... other vendor-specific bits (currentTimestampSql, upsertHint, …) added when first needed
}
```

- All Flyway migrations live in `db/migration/` and use **portable ANSI SQL only**.
- Vendor-specific overrides go in `db/migration/{vendor}/` and are layered conditionally.
- JPA uses the standard Hibernate dialect; we never write a `nativeQuery` without going through a dialect-port method.
- v1 implements `MariaDbDialect`; per the "no abstraction it doesn't use" principle the port carries
  only what's needed today (the Phase 9 backup command), with room for the other bits later.

### 5.3 `ReportExporterPort`

```java
public interface ReportExporterPort {
    MediaType supportedMediaType();
    void export(ReportData data, OutputStream out);
}
```

- v1: `CsvExporter`.
- Reports are generated **asynchronously** for large datasets. A `report_job` row tracks status; the file is written to a local `reports/` directory and made downloadable.

## 6. Time-Card Computation Engine

The heart of the system. A pure service:

```java
DailyTimeCard compute(
    Employee employee,
    LocalDate date,
    ResolvedSchedule schedule,    // shift + overrides for this date
    List<PunchEvent> punches,     // all events that could affect this date
    List<Holiday> holidays,
    List<LeaveRequest> leaves,
    OrgSettings settings
);
```

### Why pure?
- **Deterministic** — same inputs always produce same output. Auditable.
- **Unit-testable** — every shift type, rounding rule, OT tier exercised in isolation.
- **Replayable** — if rules change, we can recompute the past.

### Triggers
1. **Nightly batch** (Quartz cron) — recompute the previous day for everyone.
2. **Event-driven** — on each `PunchEventIngested`, recompute that employee's day.
3. **On manual edit** — admin/manager edits a punch → recompute.
4. **On rule change** — changing a shift triggers recompute for affected assignments (bounded window).

### Output
A `DailyTimeCard` row + a `time_card_breakdown` per applied time code. See [data-model.md](data-model.md#timecard-module).

### Algorithm sketch
1. **Resolve schedule**: Temporary > User assignment > Group assignment > none.
2. **Holiday/leave gate**: If holiday or full-day approved leave, set status, breakdown to leave code, return.
3. **No punches**: If scheduled but no punches → status = ABSENT, exception emitted.
4. **Pair punches**: Group `CHECK_IN/OUT` into work intervals; `BREAK_START/END` into breaks.
5. **Apply rounding**: Per shift's rounding rules.
6. **Apply grace periods**: Late-in / early-out tolerances.
7. **Deduct breaks**: Auto-deducted meals + tracked breaks.
8. **Slice by OT tiers**: First N hours → Attendance code, then OT-A, then OT-B…
9. **Compute rated minutes**: Each slice's minutes × time-code rate.
10. **Detect exceptions**: Missing punch, unauthorized OT, etc.
11. **Persist** `DailyTimeCard` + breakdown + exceptions.

### Floating-shift selection
For a floating shift, the first `CHECK_IN` time is matched against the configured candidate shifts; the closest start time wins. The selection is recorded in `DailyTimeCard.resolved_shift_id` so reports stay stable.

## 7. Security Model

### Authentication
- **JWT**: 15-min access token (in memory on client), 7-day refresh token (HttpOnly secure cookie).
- **Refresh token rotation** with reuse detection — old tokens are invalidated on refresh; if a revoked token is presented, the whole token family is invalidated.
- **Password hashing**: BCrypt cost 12.
- **Login throttling**: bucket4j on `/api/v1/auth/login` (5 attempts / 15 min / IP+username).

### Authorization
- **Roles**: `ADMIN`, `HR_MANAGER`, `MANAGER`, `EMPLOYEE`.
- **Permission codes**: fine-grained (`schedule.write`, `timecard.edit`, `report.run`, …) — roles bundle permissions.
- **Method-level** `@PreAuthorize("hasAuthority('schedule.write')")` on every protected method.
- **Row-level** for managers: a `ManagerScopeFilter` injects a `WHERE employee_id IN (manager_visible_employees)` predicate via Hibernate filters.

### Audit
- `@Auditable` annotation on entities triggers a JPA entity listener.
- Every create/update/delete writes an `audit_event` row with actor, action, before/after JSON, IP, user-agent, timestamp.
- The audit log is **append-only** — no UI or API path deletes rows. Retention purge is a separate scheduled job with explicit policy.

### Transport & CORS
- HTTPS in production (HTTP 3000 / HTTPS 3002 per SRS, configurable).
- CORS locked to frontend origin via `application.yml` allow-list.
- Security headers: HSTS, X-Content-Type-Options, X-Frame-Options DENY, CSP.

## 8. Cross-Cutting Concerns

### 8.1 Time & Timezone
- **Storage**: all timestamps are `TIMESTAMP WITH TIME ZONE` (or DB equivalent), normalized to UTC.
- **Display**: each employee has an effective timezone (from their department / org default). Shift rules evaluate in that timezone.
- **`org_settings.default_timezone`** governs orgs without explicit overrides.
- **`Clock`** is injected wherever needed; tests use a fixed `Clock` for determinism.

### 8.2 Caching
- Caffeine, in-process, TTL-based.
- Caches: time codes by id, shifts by id, holidays by year, role → permissions.
- Cache eviction via Spring `@CacheEvict` on writes.

### 8.3 Async & Scheduling
- **Quartz** is the intended home for cron jobs; v1 uses Spring `@Scheduled` as a thin stand-in
  (nightly recompute, retention purge, backup). The backup/retention triggers read their cron from
  `system_setting` via a `SchedulingConfigurer` dynamic `Trigger`, so an admin can re-schedule them
  from the Settings page without a restart.
- **Spring `@Async`** thread pools for report generation (`reportExecutor`) and backups
  (`backupExecutor`).
- Job state persisted in `report_job` / `backup_job` (so a restart doesn't lose work).

### 8.4 Observability
- Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/metrics`, behind admin auth).
- Micrometer → Prometheus-compatible metrics.
- Structured JSON logs (Logback + Logstash encoder).
- Trace IDs propagated via `X-Request-Id`.

### 8.5 Validation
- Bean Validation (`jakarta.validation`) on every request DTO.
- Business invariants validated in domain services, not controllers.
- Global `@ControllerAdvice` translates exceptions to RFC 7807 problem-details responses.

## 9. Frontend Architecture

### Structure
Feature folders mirror backend modules:

```
src/
├── app/                     router, providers, layouts
├── lib/                     api client, auth, error handling, types
├── components/              cross-feature UI primitives
├── theme/                   Mantine theme, tokens
├── features/
│   ├── auth/
│   ├── identity/
│   ├── organization/
│   ├── device/
│   ├── ingestion/
│   ├── timecode/
│   ├── shift/
│   ├── schedule/
│   ├── timecard/
│   ├── leave/
│   ├── exception/
│   ├── report/
│   └── admin/
└── main.tsx
```

### Each feature owns
- `routes.tsx` — sub-routes
- `api.ts` — generated client wrappers + react-query hooks
- `pages/` — page components
- `components/` — feature-specific components
- `types.ts` — TypeScript types (mostly generated)

### State
- **Server state** → TanStack Query (cache, refetch, mutations, optimistic updates).
- **Client state** → Zustand for cross-route stores (auth user, theme).
- **Form state** → React Hook Form + Zod for validation.

### Auth flow
1. `POST /auth/login` → receive access token (in JSON) + refresh cookie set by server.
2. Access token stored in memory only (never localStorage).
3. Axios interceptor adds `Authorization: Bearer …` to every request.
4. On `401`, interceptor calls `POST /auth/refresh` (refresh cookie sent automatically) → new access token → retry original request.
5. On refresh failure → redirect to login.

### Routing
- Public: `/login`, `/forgot-password`.
- Authenticated: everything else, gated by role.
- Sidebar menu items are filtered by the current user's permissions.

### API client
- Generated from the live OpenAPI spec via `openapi-typescript` (types) + thin axios wrapper.
- Regenerated as part of `npm run dev` against a running backend.

## 10. Persistence

### Schema management
- **Flyway** with versioned, portable migrations in `backend/src/main/resources/db/migration/`.
- Migrations are immutable once merged; corrections go in a new migration.
- **Repeatable migrations** (`R__*.sql`) only for views and stored programs — none in v1.

### ID strategy
- **UUID v7** for every PK. Time-ordered, so B-tree inserts stay sequential; safe to expose externally.
- Stored as `BINARY(16)` in MariaDB (smallest, fastest) with a converter; exposed as canonical string in APIs.

### Auditing columns
Every table has: `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (optimistic locking).

### Transactions
- `@Transactional` on application service methods.
- Read-only services explicitly mark `@Transactional(readOnly = true)`.
- No transactions in controllers.

## 11. Sequence: Punch → Time Card

```
Device / REST adapter         Backend                       DB
─────────────────────────────────────────────────────────────────
   ─POST /api/v1/ingestion/punches─►
                              ingest()
                              validate + idempotency check ──►(unique check)
                              resolve credential → employee
                              persist PunchEvent ────────────►(INSERT)
                              publish PunchEventIngested
                                │
                   TimeCardRecomputeListener
                                │
                      load punches for (employee, date) ◄────
                      load schedule, holidays, leaves    ◄────
                      compute() → DailyTimeCard
                      upsert DailyTimeCard + breakdown ─────►(UPSERT)
                      emit exceptions if any           ─────►(INSERT)
   ◄────200 OK { accepted: N, skipped: M }────
```

## 12. Sequence: Manual Punch Edit (with audit)

```
Manager UI                    Backend                       DB
─────────────────────────────────────────────────────────────────
   ─PUT /api/v1/timecards/{id}/punches/{punchId}─►
                              authorize (manager scope)
                              load original punch
                              persist edited punch ─────────►(UPDATE)
                              write TimeCardEdit row ───────►(INSERT)
                              write AuditEvent row ─────────►(INSERT)
                              trigger recompute
                              compute() + persist ──────────►(UPSERT)
   ◄──── 200 OK { timecard } ────
```

## 13. Deployment

### Local dev
```
docker compose up      # mariadb on :3306, backend on :3000, frontend on :5173
```

### Production (single-node)
- Spring Boot fat JAR + Vite static build served by Nginx (TLS termination).
- MariaDB on the same or a separate host.
- Volumes mounted for: DB data, `reports/`, `backups/`, log files.
- Backups: scheduled `mysqldump` job to `backups/` directory; rotation policy configurable.

### Configuration
- `application.yml` — defaults
- `application-prod.yml` — production overrides
- Environment variables for secrets (DB password, JWT signing key).
- Frontend config via `VITE_*` env vars at build time.

## 14. What's Not in v1

These are explicitly deferred. Each has a future home in the architecture:

- Real biometric device SDK integration → new ingestion adapter.
- SMS / push notifications → `NotificationPort` implementations.
- Payroll system export → `ReportExporterPort` implementations.
- Multi-tenancy → would require a `tenant_id` column added to every table + Hibernate filter; the modular monolith already supports it without restructuring.
- Mobile apps → REST API is the contract; native apps can be added without backend changes.
- High availability → DB replication, stateless backend already supports horizontal scaling once added.

## 15. Glossary

| Term | Meaning |
|---|---|
| **Punch / punch event** | A timestamped event from a device (check-in, check-out, break start/end). |
| **Time card** | Computed daily summary of an employee's worked time. |
| **Time code** | A classification of time (regular, OT-A, OT-B, sick leave, …) with a rate. |
| **Shift** | A work pattern (start, end, breaks, OT rules). |
| **Schedule template** | A reusable cycle of shifts (e.g., Mon–Fri Day, Sat–Sun off). |
| **Assignment** | Application of a template to a user/group over a date range. |
| **Temporary schedule** | Per-employee override for a specific date range. |
| **Exception** | A detected anomaly (missing punch, unauthorized OT, absent without leave). |
| **Ingestion source** | A configured origin for punch events (REST endpoint, future device, CSV, …). |

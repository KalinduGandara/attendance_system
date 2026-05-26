# Implementation Plan

> Phased plan for building the Attendance System. Each phase has explicit goals, deliverables, and acceptance criteria. Phases are sequenced for the smallest possible "vertical slice" first, then steady additions. Phases 5 and 7 are the highest-risk and get extra detail.

## Guiding Principles

1. **Vertical slices over horizontal layers.** Each phase ends with something runnable end-to-end (DB → API → UI) for its slice. No phase leaves the system half-broken.
2. **Tests are part of the deliverable.** A phase is not done until its core tests pass in CI.
3. **No phase introduces an abstraction it doesn't immediately use.** Resist designing for hypothetical phase N+3.
4. **Migrations are append-only.** Never edit a merged migration.
5. **Docs stay current.** If a phase's reality diverges from `architecture.md` or `data-model.md`, update those first, then code.

## Phase Map

| Phase | Theme | Duration estimate |
|---|---|---|
| 0 | Foundation: scaffolding, CI, auth | 1 week |
| 1 | Identity & Organization | 1 week |
| 2 | Devices, Credentials, Ingestion Sources | 4 days |
| 3 | Time Codes & Shifts | 1 week |
| 4 | Scheduling & Rostering | 1 week |
| 5 | **Punch Ingestion + Time-Card Engine** | 2 weeks |
| 6 | Time Card UI (calendar + list + manual edit) | 1 week |
| 7 | Leaves & Exceptions | 1 week |
| 8 | Reporting | 1 week |
| 9 | System Admin (audit viewer, backups, retention) | 4 days |
| 10 | Hardening: perf, security review, accessibility, ops docs | 1 week |

Estimates assume one engineer working focused. Adjust to reality, not vice versa.

---

## Phase 0 — Foundation

**Goal:** Repo skeleton, dev environment, CI, end-to-end auth slice.

**Backend deliverables**
- Maven multi-module-free `backend/` project with package layout from [architecture.md §3](architecture.md#3-backend-module-map).
- Spring Boot 3.x application with profiles `dev`, `test`, `prod`.
- Flyway baseline migration: empty schema + `user`, `role`, `permission`, `role_permission`, `user_role`, `refresh_token`.
- UUID v7 generator utility + JPA `AttributeConverter` for `BINARY(16)`.
- Spring Security configured for JWT (HS256 signing key from env).
- Endpoints: `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`, `POST /auth/change-password`.
- Global exception handler producing RFC 7807 responses.
- `@Auditable` annotation + JPA listener writing to `audit_event` (table created in this phase).
- OpenAPI spec at `/api/openapi.json` + Swagger UI.
- Actuator endpoints behind admin auth.
- Seed data: one `ADMIN` user, all role + permission rows.

**Frontend deliverables**
- Vite + React 18 + TS skeleton in `frontend/`.
- Mantine theme, app shell with sidebar + header.
- TanStack Query + axios with refresh interceptor.
- Auto-generated TS types from OpenAPI (`openapi-typescript`).
- Pages: `/login`, `/`, `/forbidden`, `/not-found`.
- Auth Zustand store; protected-route wrapper.

**Infra deliverables**
- `docker-compose.yml`: MariaDB + backend + frontend (dev).
- GitHub Actions (or local script) for: backend build + test + Flyway validation; frontend lint + type-check + build; E2E happy-path login (Playwright).

**Acceptance criteria**
- `docker compose up` brings the system up.
- Admin can log in via the React UI and see an empty dashboard.
- Forcing a `401` triggers refresh; refresh failure redirects to login.
- All Flyway migrations run clean from empty schema.
- CI green on a fresh clone.

---

## Phase 1 — Identity & Organization

**Goal:** Manage users, roles, employees, departments, groups, custom fields, holidays.

**Backend**
- Tables: `employee`, `department`, `user_group`, `employee_group`, `custom_field_definition`, `custom_field_value`, `holiday`, `holiday_group`.
- CRUD APIs for each.
- Bulk employee import (CSV) — synchronous if < 1000 rows, async job otherwise (lays groundwork for report jobs).
- Manager-scope service: given a manager user, return the set of visible employee ids (used later for row-level filtering).
- Audit on every write.

**Frontend**
- Pages: Users list/detail, Employees list/detail (with custom fields), Departments tree, Groups tree, Holidays calendar.
- Forms with React Hook Form + Zod.
- Permission-gated buttons (hidden when user lacks permission).

**Tests**
- Repository tests against Testcontainers MariaDB for hierarchy queries (department tree, group tree).
- Controller tests with MockMvc covering auth + validation.
- E2E (Playwright): admin creates a department, adds an employee, sees them in the list.

**Acceptance**
- An admin can fully manage org structure via the UI.
- Importing 5,000 employees from CSV completes successfully and is recorded in audit.

---

## Phase 2 — Devices, Credentials, Ingestion Sources

**Goal:** Set up the ingestion plumbing so Phase 5 can hook directly in.

**Backend**
- Tables: `device`, `ingestion_source`, `device_ingestion_source`, `credential`.
- CRUD APIs.
- `IngestionSource` API-key generation + hashing on create.
- Credential resolution service: given `(type, value)` → return matching active credential's employee, or null.
- A single seeded ingestion source of type `REST` named "Default Web Ingestion".

**Frontend**
- Pages: Devices, Ingestion Sources (with API key shown once on creation), Credentials per employee.

**Tests**
- Credential uniqueness + lookup hash test.
- API key auth integration test for a future ingestion endpoint (stubbed).

**Acceptance**
- An admin can create a REST ingestion source, see its API key once, and assign credentials to employees.

---

## Phase 3 — Time Codes & Shifts

**Goal:** All shift configuration done before the engine arrives in Phase 5.

**Backend**
- Tables: `time_code`, `shift`, `shift_segment`, `shift_rounding_rule`, `shift_grace_rule`, `break_rule`, `overtime_rule`, `floating_shift_candidate`.
- Validations: time-code rate ∈ [0, 10]; OT tier sequence is strictly increasing; floating candidates must be `FIXED` or `FLEXIBLE`.
- Caching of time codes + shifts (Caffeine).
- APIs return nested rules; updates use atomic full-replacement of children.

**Frontend**
- Time Codes page with color picker.
- Shift editor: complex form with type tabs (Fixed/Flex/Floating), break sub-forms, OT tier table.
- Visual schedule preview based on segments.

**Tests**
- Domain tests: shift validators for each shift type.
- E2E: create a Fixed shift with two OT tiers and a 30-min lunch deduction.

**Acceptance**
- All shift types from SRS §3.3 can be modeled.
- Updating a shift bumps the optimistic-lock version; concurrent edits show a 409 with the latest version.

---

## Phase 4 — Scheduling & Rostering

**Goal:** Assign shifts to people across time.

**Backend**
- Tables: `schedule_template`, `schedule_template_day`, `schedule_assignment`, `temporary_schedule`.
- Schedule resolver: `resolveShift(employee, date) → ResolvedSchedule` (priority: temporary > employee assignment > group assignment > none). Pure function, fully tested.
- APIs for template CRUD, assignment CRUD, temporary schedule CRUD.

**Frontend**
- Schedule Templates page: drag-and-drop week builder (shifts onto day cells).
- Assignments page: target picker (employee or group), template picker, date range.
- Temporary schedule page: per-employee calendar with shift override.

**Tests**
- Resolver tests: priority order, holidays, edge dates, time-zone boundaries.
- E2E: assign a weekly template to a group, see correct shift per day for a member.

**Acceptance**
- For any (employee, date), the resolver returns a deterministic shift or "off".
- SRS FR-4.4 (tiered overtime per shift) flows correctly through resolution.

---

## Phase 5 — Punch Ingestion + Time-Card Engine ⭐

This is the riskiest, most-tested phase. It gets the most upfront design.

**Goal:** Accept punches, compute time cards deterministically, emit exceptions.

### 5.1 Ingestion adapter
- Tables: `punch_event` (with idempotency unique index).
- `POST /api/v1/ingestion/punches` accepting batches.
- Authentication via either JWT (admin/manager) or `X-Source-Api-Key`.
- `Idempotency-Key` header for whole-batch dedup; per-event idempotency via `(source_id, external_event_id)`.
- Credential resolution; unresolved events stored with `status=UNRESOLVED` for later reconciliation.
- Domain event `PunchEventIngested` published after persistence.

### 5.2 Time-card computation engine
- Tables: `daily_time_card`, `time_card_breakdown`, `time_card_edit` (next phase uses edits).
- Pure service `TimeCardCalculator` with signature from [architecture.md §6](architecture.md#6-time-card-computation-engine).
- Subroutines, each independently testable:
  - `PunchPairer` — turns event list into intervals.
  - `Rounder` — applies rounding rules.
  - `GraceApplier` — applies late-in / early-out tolerances.
  - `BreakDeducer` — auto-deduct + tracked breaks.
  - `OvertimeTierSlicer` — chronological assignment to OT tiers.
  - `RatedMinutesCalculator` — applies rate per code.
  - `ExceptionDetector` — emits exceptions.
- `TimeCardRecomputeService` orchestrates the engine for a given (employee, date).

### 5.3 Recompute triggers
- Event-driven: `PunchEventIngested` → recompute that employee/date.
- Nightly Quartz job: recompute previous day for all active employees.
- Programmatic API (used by Phase 6 manual edits, Phase 4 schedule changes).

### 5.4 Reconciliation tooling
- Unresolved punches queue with retry/assign UI groundwork (full UI in Phase 6).

### 5.5 Test plan
- **Golden test suite**: a YAML fixture per scenario (`fixtures/timecard/`) describing inputs (shift, schedule, punches, holidays, leaves) + expected output. The test loader runs every fixture through the engine. Coverage targets:
  - Fixed shift: on time, late, early, missing in, missing out, both missing, overtime, multiple OT tiers, meal deduct, tracked break, day-off with punches (orphan), holiday with punches, leave with punches, cross-midnight shift, DST transitions.
  - Flexible shift: meets required hours, under, over.
  - Floating shift: matched correctly, no match → falls back, ambiguous match.
  - Rounding: each mode (UP/DOWN/NEAREST), each unit.
- **Property-based tests** (jqwik): for any random punch sequence in a fixed shift, `workedMinutes + breakMinutes ≤ wall-clock minutes covered`.
- **Idempotency test**: ingest same batch twice → same result; second response shows all `DUPLICATE`.
- **Concurrency test**: parallel ingestion of 1000 events for the same employee/day → final time card is correct.

**Acceptance**
- All golden fixtures pass.
- Ingesting punches in the dev environment results in a populated time card for that employee on the next API read.
- Recomputing the same day twice produces byte-identical output (deterministic).

---

## Phase 6 — Time Card UI

**Goal:** Visual time card management per SRS §3.5.

**Backend**
- `GET /timecards` with filters (employee, date range, group, status).
- `POST /timecards/{id}/edits` — manual edit endpoint per [api-contracts.md §4.3](api-contracts.md#43-time-card-edit--post-timecardsidedits). Synchronous recompute.

**Frontend**
- Time Card dashboard with toggle: Calendar View / List View.
- Calendar uses FullCalendar; time codes shown by color from `time_code.color`.
- Click a day → side panel with raw punches, breakdown, exceptions, edit history.
- Edit punch: time picker + reason field (required).
- Retroactive leave registration entry point (deep-links into Phase 7's leave form).
- Unresolved punches queue page (admin/HR) with credential assignment.

**Tests**
- E2E: ingest a late punch → admin sees `LATE_IN` exception on the time card → edits the punch → exception clears.
- E2E: try to edit a punch without a reason → form blocks submission.

**Acceptance**
- All actions in SRS FR-5.2 work via the UI.

---

## Phase 7 — Leaves & Exceptions

**Goal:** Leave lifecycle + exception resolution workflow.

**Backend**
- Tables: `leave_type`, `leave_balance`, `leave_request`, `exception_event`.
- APIs: leave type CRUD, balance read/adjust, request lifecycle (create/cancel/approve/reject), retroactive flag.
- Approved leaves feed into time-card recompute for the affected date range.
- Exception detection (already in Phase 5) now exposed via APIs with filters; `PATCH /exceptions/{id}/resolve`.

**Frontend**
- Leave Types admin page (linked to a `LEAVE`-category time code).
- Employee Leave page: balance view, request form, history.
- Manager Approvals page: pending requests, approve/reject with reason.
- Exceptions page: filterable queue, bulk-resolve, drilldown to time card.

**Tests**
- Approving a leave that covers a previously-`ABSENT` day → recompute changes status to `LEAVE` and closes the absent exception.
- Half-day leave correctly halves balance and partially fills the time card.

**Acceptance**
- SRS FR-5.2 (retroactive leave) and exception management fully usable.

---

## Phase 8 — Reporting

**Goal:** Generate SRS §3.6's seven reports, exportable as CSV.

**Backend**
- Tables: `report_job`.
- `ReportExporterPort` + `CsvExporter`.
- Report types implemented:
  1. **Daily** — punch-level rows per employee per day in range.
  2. **Daily Summary** — one row per (employee, day): totals.
  3. **Individual** — full detail for one employee over range.
  4. **Individual Summary** — one row per employee: period totals.
  5. **Leave** — leave requests/usage per employee.
  6. **Exception** — exceptions by type/status.
  7. **Modified Punch Log History** — all manual edits.
- Async generation via `@Async`; row count + file path in `report_job`.
- Custom user fields are includable via `parameters.includeCustomFields`.

**Frontend**
- Reports page: pick type → parameter form → run → poll status → download.
- Saved-parameter presets per user (Phase 9 admin can promote to org-wide).

**Tests**
- Each report has a golden CSV fixture compared byte-for-byte (with stable sort).
- Report job survives backend restart (state persisted; long-running job not in-memory only).

**Acceptance**
- All seven reports generate correctly with sample data; CSVs open cleanly in Excel and a CSV parser.

---

## Phase 9 — System Admin

**Goal:** Operational tooling per SRS §4.

**Backend**
- `audit_event` viewer endpoint (already populated since Phase 0).
- Scheduled backups: Quartz job runs `mysqldump`-equivalent (via the dialect port) to `backups/`. Records `backup_job` row. Configurable cron in `system_setting`.
- Retention policies: `retention_policy` table; Quartz job purges per policy with bounded batches; results recorded.
- System settings editor with type-aware validation.

**Frontend**
- Audit Log page: filters by actor, entity, date; pagination; drilldown to before/after JSON.
- Backups page: list + manual "Run now" + download link.
- Retention page: per-entity toggle + retention days.
- System Settings page.

**Tests**
- Audit row exists for every CRUD action in the existing E2E suite (regression check).
- Retention purge respects the configured day count and never touches recent data.

**Acceptance**
- An admin can audit any change, run/inspect backups, and configure retention.

---

## Phase 10 — Hardening

**Goal:** Production readiness.

- Performance: load test ingestion at 500 events/sec; recompute SLO < 100 ms per (employee, day) at p95.
- Indexes: review actual query plans on the dev dataset; add what's missing.
- Security review (run `/security-review`): check OWASP top 10, dependency vulnerabilities, secrets handling, CORS, headers.
- Accessibility: keyboard nav across all forms, screen-reader labels on every interactive element.
- Internationalization scaffolding: every UI string goes through i18n, English bundle complete (translations later).
- Ops docs: backup/restore drill, common incidents, escalation.
- Release checklist + changelog.

**Acceptance**
- Load test passes targets.
- Security review issues all resolved or filed with mitigations.
- WCAG AA on critical flows (login, time card view, leave request).

---

## Cross-Phase Practices

### Definition of Done (per phase)
- All listed deliverables shipped.
- Tests green (unit + integration + at least one E2E for the new flow).
- OpenAPI spec updated; frontend types regenerated.
- `data-model.md` and `api-contracts.md` reflect reality.
- New permissions seeded; roles updated; readme/CHANGELOG updated.
- Audit log records the new entity's CRUD.

### Code review checklist
- No cross-module repository access.
- No `@Transactional` in controllers.
- No vendor-specific SQL outside the dialect port.
- No `localStorage` token storage.
- Every new entity has audit fields + optimistic locking.
- Every new endpoint has `@PreAuthorize` and an OpenAPI summary.

### Risk register
| Risk | Mitigation |
|---|---|
| Time-card engine bugs ship to prod | Golden-fixture suite + property tests + parallel-recompute test in CI |
| MariaDB-specific SQL leaks in | All native queries go through `DatabaseDialectPort`; CI runs a "schema-portability lint" that grep-checks for known vendor functions |
| JWT signing key compromise | Key rotation procedure documented in runbook; refresh-token family invalidation on suspected breach |
| Retention purge deletes audit log | Audit retention disabled by default; UI shows a "this cannot be undone" warning |
| Floating-shift ambiguity surprises users | UI shows which candidate was selected and why; supportable via the `resolved_shift_id` field on the time card |

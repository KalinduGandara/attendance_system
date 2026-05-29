# Changelog

All notable changes to the Attendance System are documented here. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Schema/DB migrations are append-only (Flyway `V1..Vn`); a release's migration
range is noted under each version.

## [Unreleased]

## [0.1.0] — 2026-05-30

First end-to-end release. Phases 0–10 of [docs/plan.md](docs/plan.md). Migrations
`V1`–`V12`.

### Added — Phase 10 (Hardening)
- HTTP security response headers (CSP, HSTS, `X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`) on every response.
- OWASP Top-10 security review ([docs/security-review.md](docs/security-review.md));
  Swagger UI / OpenAPI schema disabled in the `prod` profile.
- Index review migration `V12` (punch-event time scan, report-job retention scan,
  open-exception queue).
- Prometheus registry + `timecard_recompute_seconds` and
  `ingestion_punches_total{status}` metrics behind `/actuator/prometheus`.
- Performance harness: k6 ingestion load test ([perf/](perf/)) and an in-process
  recompute p95 guard (`RecomputeBenchmarkTest`).
- Frontend i18n scaffolding (i18next + complete English bundle); shell, login,
  error pages, dashboard, and the time-card dashboard migrated to `t()`.
- Accessibility: skip-to-content link, `nav`/`main` landmarks, document `lang`,
  ARIA labelling on critical flows.
- Ops docs: release checklist, restore drill, escalation matrix, this changelog.

### Added — Phases 0–9
- **Phase 0** — Auth slice (JWT access + opaque refresh), Flyway schema, audit
  infrastructure, OpenAPI/Swagger, Mantine app shell, dev docker-compose, CI.
- **Phase 1** — Identity & organization: users, employees, departments, groups,
  custom fields, holidays; CSV import; manager-scope service.
- **Phase 2** — Devices, ingestion sources (hashed API keys), credentials;
  `X-Source-Api-Key` auth filter.
- **Phase 3** — Time codes & shifts (Fixed/Flex/Floating) with nested rules and
  Caffeine caching.
- **Phase 4** — Scheduling & rostering; pure `ScheduleResolver`.
- **Phase 5** — Punch ingestion + pure, deterministic time-card engine; golden
  fixtures, property tests, idempotency, concurrency.
- **Phase 6** — Time-card UI (calendar/list, drilldown, manual edits with audit).
- **Phase 7** — Leaves & exceptions lifecycle; recompute integration.
- **Phase 8** — Reporting: seven report types, async generation, CSV export.
- **Phase 9** — System admin: audit viewer, type-aware settings, scheduled
  backups via the dialect port, retention policies; live-reschedulable crons.

### Security
- Passwords BCrypt(12); refresh tokens and API keys stored SHA-256 hashed.
- Account lockout on repeated failed logins; refresh-token family invalidation.
- All persistence parameterized via JPA; vendor SQL confined to the dialect port;
  `mysqldump` password passed via `MYSQL_PWD`, never argv.

### Known issues
- `npm audit`: 2 moderate advisories (esbuild dev-server, GHSA-67mh-4wv8-2f99) —
  dev-server only, not shipped in the production build. Tracked in the security
  review. Vite major upgrade deferred.

[Unreleased]: https://example.com/compare/v0.1.0...HEAD
[0.1.0]: https://example.com/releases/tag/v0.1.0

# Security Review

Phase 10 hardening review of the Attendance System against the
[OWASP Top 10 (2021)](https://owasp.org/Top10/). This is the manual,
whole-system pass that complements the automated `/security-review` run on the
Phase 10 diff. Each item records the **posture**, the **evidence** (where it
lives), and any **residual finding** with its disposition.

Legend: ✅ addressed · 🟡 accepted risk / documented mitigation · 🔧 fixed in Phase 10.

## Summary

No critical or high findings. Authentication, access control, transport, and
data-handling controls were already in place from earlier phases; Phase 10 adds
HTTP security headers, disables the API schema/Swagger surface in prod, reviews
indexes, and wires monitoring. Residual items are low severity with documented
mitigations.

## A01 — Broken Access Control ✅

- Method-level authorization: `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('…')")`
  on every controller action (e.g. `PunchIngestionController`, admin endpoints).
- Default-deny: `SecurityConfig` ends with `.anyRequest().authenticated()`; only
  an explicit allowlist (login, refresh, health, openapi/swagger, actuator
  health/info) is public.
- Actuator management endpoints beyond health/info require `system.admin`.
- Row-level scoping: `ManagerScopeService` limits managers to self + transitive
  reports; admin/HR see all.
- Frontend `ProtectedRoute` gates routes by permission, but the **server is the
  authority** — the UI gate is convenience only.
- Every write is audited (`@Auditable` → `audit_event`), giving an after-the-fact
  access trail.

## A02 — Cryptographic Failures ✅

- Passwords: BCrypt, work factor 12 (`SecurityConfig.passwordEncoder`).
- Access tokens: JWT HS256, signing key from `JWT_SECRET` env, ≥ 32 bytes, short
  TTL (900 s default).
- Refresh tokens: **opaque** (not JWTs), stored **SHA-256 hashed** (`TokenHasher`),
  with family rotation/invalidation on reuse.
- Ingestion source API keys: generated once, returned in plaintext exactly once,
  persisted only as SHA-256.
- Cookies: refresh cookie is `HttpOnly`, `SameSite=Strict`, and `Secure` in prod
  (`attendance.cookie.secure=true`).
- Transport: TLS terminated at Nginx (runbook §4); HSTS now emitted by the app
  (see A05).

🟡 The dev profile ships a fallback `JWT_SECRET`. Prod **must** set a strong
secret. *Mitigation/recommendation:* treat an unset/default secret as a
deploy-blocker in the release checklist; consider a startup assertion in a
future phase.

## A03 — Injection ✅

- All persistence goes through Spring Data JPA / parameterized HQL — no
  string-concatenated SQL anywhere (`grep` of repositories shows only `:named`
  parameters).
- Vendor-specific SQL is confined to the `DatabaseDialectPort` (ADR-0003).
- Backups: `mysqldump` argv is built as a **list** (no shell interpolation) and
  the DB password is passed via the `MYSQL_PWD` environment variable, never on
  the command line (`MariaDbDialect`, verified by `MariaDbDialectTest`).
- CSV export is RFC 4180-encoded; values are quoted, mitigating CSV/formula
  injection in spreadsheet apps. (See 🟡 below.)

🟡 CSV formula injection: a cell beginning with `= + - @` can be interpreted as a
formula by Excel. Low risk (export is admin-initiated, RFC 4180 quoting applied).
*Recommendation:* prefix risky leading characters with `'` if untrusted free-text
fields are ever exported.

## A04 — Insecure Design ✅

- Modular monolith with enforced module boundaries (ADR-0001); no cross-module
  repository access (per the code-review checklist in plan.md).
- The time-card engine is a **pure** function with a golden-fixture + property
  test suite — deterministic, replayable, auditable.
- Idempotency on ingestion (whole-batch key + per-event natural key); optimistic
  locking (`version`) on every entity.
- Retention purge is disabled by default; audit retention carries the longest
  window; `punch_event` referenced by a manual edit is never purged (FK-safe).

## A05 — Security Misconfiguration 🔧

- 🔧 **HTTP security headers** added in `SecurityConfig`: `Content-Security-Policy`
  (`default-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`,
  `base-uri 'self'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`
  (camera/mic/geo/etc. disabled), and HSTS (1 year, includeSubDomains, preload)
  over HTTPS. Covered by `SecurityHeadersTest`.
- 🔧 **API schema/Swagger disabled in prod** (`application-prod.yml`:
  `springdoc.api-docs.enabled=false`, `swagger-ui.enabled=false`) so the schema
  isn't anonymously enumerable; in dev it remains available.
- CORS: origins from `CORS_ORIGINS` (allowlist, no wildcard with credentials),
  explicit method/header lists.
- Error responses are RFC 7807 problems with `include-message: never` and
  `include-stacktrace: never` — no internal detail leakage.
- `open-in-view: false`; `ddl-auto: validate` (schema owned by Flyway).

🟡 CSP allows `'unsafe-inline'` for `script-src`/`style-src` **because the bundled
Swagger UI requires it**. The app is an API (JSON, not rendered in a browser
context) and the production SPA is served by Nginx with its own strict CSP. With
Swagger disabled in prod (above), the inline allowance is dev-only. *Recommendation:*
move to nonce-based CSP if the backend ever serves first-party HTML.

## A06 — Vulnerable & Outdated Components 🟡

- Backend deps are pinned (Spring Boot 3.3.4 BOM, explicit versions for jjwt,
  mariadb, testcontainers, …). Flyway migrations are append-only.
- `npm audit`: **2 moderate** advisories, both the
  [esbuild dev-server issue (GHSA-67mh-4wv8-2f99)](https://github.com/advisories/GHSA-67mh-4wv8-2f99)
  reached transitively via Vite.

🟡 The esbuild advisory affects the **Vite dev server only** — it does not ship in
the production build (`dist/` is static and served by Nginx). *Disposition:*
accepted; the dev server must not be exposed publicly. The fix (`vite@8`) is a
major bump deferred to a dependency-maintenance task. *Recommendation:* enable
Dependabot + an OWASP dependency-check / `npm audit` gate in CI.

## A07 — Identification & Authentication Failures ✅

- Account lockout: `AuthService` increments `failed_login_count` and sets
  `locked_until`; a locked account is rejected before password check.
- Short-lived access tokens + opaque rotating refresh tokens; logout revokes the
  refresh family.
- Password change endpoint; `password_min_length` is an org setting.
- Generic auth-failure responses (no username enumeration via differing errors).

🟡 No IP/endpoint rate limiting on `/auth/login` beyond per-account lockout.
*Mitigation:* per-account lockout blunts credential stuffing against a single
account; a reverse-proxy rate limit (Nginx `limit_req`) is the recommended
network-layer control (added to the release checklist).

## A08 — Software & Data Integrity Failures ✅

- Flyway migrations are append-only and validated on boot; no runtime DDL.
- Full audit trail with before/after JSON for every write.
- Optimistic locking prevents lost updates; concurrent edits surface a 409.
- Scheduled backups + documented restore drill (runbook §5).

## A09 — Security Logging & Monitoring Failures ✅

- `audit_event` row for every CRUD/auth-relevant action, including a regression
  test that a settings change is audited.
- Structured logs tagged with `requestId`.
- Metrics: Micrometer + Prometheus (`/actuator/prometheus`), including
  `timecard_recompute_seconds` and `ingestion_punches_total{status}` (Phase 10).
  Alert thresholds documented in runbook §7.

## A10 — Server-Side Request Forgery (SSRF) ✅ (N/A)

- The system makes no outbound HTTP requests to user-controlled URLs. Ingestion
  is inbound; there are no webhooks, URL fetchers, or proxy features. No SSRF
  surface.

## Accessibility note (not OWASP, tracked here for the hardening phase)

Critical flows hardened for WCAG 2.1 AA: `lang="en"` on the document, a
skip-to-content link, `nav`/`main` landmarks with labels, accessible form labels
(Mantine renders real `<label>` + `aria` wiring), and `role="alert"` on the login
error. 🟡 Some list rows use click handlers that aren't yet keyboard-focusable —
tracked for incremental cleanup; primary forms and navigation are fully
keyboard-operable.

## Action items carried forward

| # | Item | Severity | Disposition |
|---|---|---|---|
| 1 | Fail startup on unset/default `JWT_SECRET` in prod | Low | Release-checklist gate now; code assertion later |
| 2 | CI dependency scanning (Dependabot / `npm audit` gate) | Low | Recommended |
| 3 | Upgrade Vite (esbuild advisory, dev-only) | Low | Deferred (major bump) |
| 4 | Nginx `limit_req` on `/auth/login` | Low | Ops config |
| 5 | Keyboard focus on clickable table rows | Low | Incremental a11y cleanup |

# Runbook

Operational reference for running the Attendance System in dev and prod.

## 1. Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 LTS |
| Maven | 3.9+ |
| Node | 20 LTS |
| npm | 10+ |
| Docker | 24+ |
| Docker Compose | v2 |
| MariaDB | 10.11+ (matched by `docker-compose.yml`) |

## 2. Local Development

### First-time setup
```
git clone <repo>
cd attendance_system2
docker compose up -d mariadb
cd backend && ./mvnw flyway:migrate && cd ..
cd backend && ./mvnw spring-boot:run    # backend on :3000
cd frontend && npm install && npm run dev   # frontend on :5173
```

### Day-to-day
```
docker compose up                  # starts everything
docker compose up -d --build       # rebuild after changes
docker compose logs -f backend     # tail backend logs
docker compose down                # stop, keep data
docker compose down -v             # stop and wipe DB
```

### Useful Maven goals
| Goal | Purpose |
|---|---|
| `./mvnw verify` | full build + tests |
| `./mvnw test -Dtest=TimeCardCalculatorTest` | run a single test class |
| `./mvnw flyway:info` | show migration status |
| `./mvnw flyway:migrate` | apply pending migrations |
| `./mvnw flyway:repair` | recover from a failed migration |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | run with dev profile |

### Useful npm scripts
| Script | Purpose |
|---|---|
| `npm run dev` | Vite dev server |
| `npm run build` | production build → `dist/` |
| `npm run test` | Vitest |
| `npm run test:e2e` | Playwright |
| `npm run lint` | ESLint |
| `npm run typecheck` | tsc --noEmit |
| `npm run gen:api` | regenerate API types from running backend OpenAPI |

## 3. Configuration

Backend config layered as: `application.yml` → `application-{profile}.yml` → environment variables.

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:mariadb://localhost:3306/attendance` | |
| `DB_USERNAME` | `attendance` | |
| `DB_PASSWORD` | `change-me` | required in prod |
| `JWT_SECRET` | (none) | required; HS256 signing key, ≥ 32 bytes |
| `JWT_ACCESS_TTL_SECONDS` | `900` | |
| `JWT_REFRESH_TTL_SECONDS` | `604800` | |
| `CORS_ORIGINS` | `http://localhost:5173` | comma-separated |
| `BACKUP_DIR` | `./backups` | volume-mounted in prod |
| `REPORT_DIR` | `./reports` | volume-mounted in prod |
| `SERVER_PORT_HTTP` | `3000` | per SRS §2.1 |
| `SERVER_PORT_HTTPS` | `3002` | per SRS §2.1 |
| `LOG_LEVEL` | `INFO` | |

Frontend config — set at build time via Vite env:
| Variable | Default |
|---|---|
| `VITE_API_BASE_URL` | `/api/v1` |
| `VITE_APP_NAME` | `Attendance` |

## 4. Production Deployment (single-node)

### Topology
```
┌──────────┐    ┌────────────────────┐    ┌──────────┐
│  Nginx   │ ─► │ Spring Boot JAR    │ ─► │ MariaDB  │
│ (TLS)    │    │ (backend on :3000) │    │  :3306   │
└──────────┘    └────────────────────┘    └──────────┘
     ▲
     │ static files
     ▼
┌──────────┐
│  /dist   │  Vite build output served by Nginx
└──────────┘
```

### Steps
1. Provision host with Docker, mount volumes:
   - `/var/lib/attendance/db` → MariaDB data
   - `/var/lib/attendance/backups` → backups
   - `/var/lib/attendance/reports` → report files
   - `/var/log/attendance` → logs
2. Copy `docker-compose.prod.yml` + `.env.prod` (with secrets).
3. `docker compose -f docker-compose.prod.yml up -d`.
4. Verify: `curl https://<host>/api/v1/health` → 200.

### TLS
Nginx terminates TLS on 3002 (per SRS §2.1) and proxies to the backend on 3000. Use cert-manager or Certbot. Cipher list: TLS 1.2+, modern profile.

### Reverse proxy headers
Nginx must pass: `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`. Backend trusts these only from the loopback / Docker network — `server.forward-headers-strategy=framework`.

## 5. Backups

### Scheduled
- Quartz job runs at the cron in `system_setting.backup_cron` (default `0 30 2 * * ?` — 02:30 daily).
- Writes a timestamped dump to `BACKUP_DIR`.
- Records a `backup_job` row.
- Rotation: keeps the last `backup_retain_count` (default 14) — older files deleted.

### Manual
```
curl -X POST https://<host>/api/v1/system/backups -H "Authorization: Bearer <admin-jwt>"
```
Or via the Backups admin page.

### Verifying a backup
1. Restore on a staging host:
   ```
   docker compose -f docker-compose.staging.yml up -d mariadb
   docker exec -i staging-mariadb mariadb -u root -p attendance < /path/to/dump.sql
   ```
2. Start backend pointing at staging DB.
3. Smoke test: log in, view a known time card, run a report.

### Disaster recovery
1. Provision fresh host per §4.
2. Restore latest backup into the MariaDB volume **before** the backend starts (Flyway will detect schema and apply nothing).
3. Start the stack.
4. Force a recompute of the last N days via admin endpoint (`POST /api/v1/admin/recompute?from=...&to=...`).

## 6. Common Operations

### Reset an admin password
The product's UI handles this. If locked out completely:
```sql
UPDATE user
SET password_hash = '<bcrypt-of-new-password>', failed_login_count = 0, locked_until = NULL, status = 'ACTIVE'
WHERE username = 'admin';
```
Then audit the manual action and rotate the password again from the UI.

### Force time-card recompute
```
POST /api/v1/admin/recompute
{ "employeeIds": ["..."] | "all", "from": "2026-05-01", "to": "2026-05-31" }
```

### Reconcile unresolved punches
1. Open the Unresolved Punches queue (admin/HR).
2. For each event, assign to the correct employee or mark as INVALID with a reason.
3. The system republishes `PunchEventIngested` for assigned ones.

### Rotate JWT signing key
1. Generate a new 32-byte key.
2. Set the new key in `JWT_SECRET`. Restart backend.
3. All access tokens immediately invalidate. Users get 401 → automatic refresh → new tokens signed with the new key.
4. Refresh-token family is not affected because refresh tokens are opaque (not JWTs).

### Rotate an ingestion source API key
1. Edit the source in the Ingestion Sources page → "Rotate Key".
2. New key shown once; old key invalidated immediately.
3. Update the external system using the source.

## 7. Monitoring & Alerts

### Health
- `GET /api/v1/health` — liveness
- `GET /actuator/health` — Spring health (DB, disk)
- `GET /actuator/info` — build info

### Metrics (Micrometer / Prometheus)
| Metric | What to alert on |
|---|---|
| `http_server_requests_seconds` | p95 > 500ms |
| `ingestion_punches_total{status="REJECTED"}` | spike |
| `timecard_recompute_seconds` | p95 > 200ms |
| `report_jobs_failed_total` | any |
| `audit_events_total` | unusual drop (system not auditing) |
| `db_connections_active` | > 80% pool |

### Logs
- JSON structured logs to stdout (Logback + Logstash encoder).
- Each request tagged with `requestId`. Audit events log at INFO.

## 8. Incident Playbooks

### Ingestion is rejecting events
1. Check `events_rejected` counter trend on the affected source.
2. Look at recent `punch_event` rows with `status='INVALID'` — `raw_payload_json` shows what came in.
3. Common causes:
   - Wrong API key (rotated recently?) → 401 in logs.
   - Credential not found → check `credential` rows for that lookup hash.
   - Event time in the future → adapter clock skew; check device.

### A time card looks wrong
1. Pull up the time card; check the punch list and computed breakdown.
2. Trigger a manual recompute (`POST /api/v1/admin/recompute?employeeId=...&from=...&to=...`).
3. If still wrong, run the engine's golden test for the closest matching scenario; reproduce the bug in a fixture before fixing.

### Backups are failing
1. Check the latest `backup_job` row's `error_message`.
2. Most common: disk full → check `BACKUP_DIR` volume.
3. Run a manual backup to confirm fix.

### DB connection exhausted
1. Check `db_connections_active` metric.
2. Look for long-running transactions (`SHOW PROCESSLIST` on MariaDB).
3. If a report job is hogging connections, kill via `DELETE /reports/{id}` or restart backend.

## 9. Upgrading

1. Read the CHANGELOG for the target version's migration notes.
2. Run a backup (manual job, wait for `DONE`).
3. Deploy the new image; Flyway applies new migrations on boot.
4. If a migration is large (long-running), schedule a maintenance window.
5. Smoke test from the [§5 Verifying a backup](#verifying-a-backup) checklist.
6. Roll back: redeploy previous image **and** restore DB from the pre-upgrade backup (migrations are forward-only).

## 10. Conventions for On-Call

- Never edit production data directly except via documented playbooks above.
- Every manual DB change must be paired with an `audit_event` insert describing actor/reason.
- Use the staging environment to reproduce before fixing in prod.
- After any incident: post-mortem, file a follow-up issue if a runbook entry is missing or wrong.

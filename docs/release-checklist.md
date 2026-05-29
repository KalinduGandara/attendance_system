# Release Checklist

Run through this for every production release. Boxes are per-release; copy this
list into the release ticket and check them off there.

## 0. Pre-flight (code freeze)

- [ ] `CHANGELOG.md` updated; the new version has a dated section and migration range.
- [ ] Version bumped (`backend/pom.xml` `<version>`, `frontend/package.json`).
- [ ] CI green on the release commit: backend `mvn verify`, frontend
      `typecheck` + `build`, Flyway validate.
- [ ] `docs/data-model.md` and `docs/api-contracts.md` reflect any schema/API change.
- [ ] Security review action items for this release triaged
      ([docs/security-review.md](security-review.md)).

## 1. Configuration & secrets (deploy-blockers)

- [ ] `JWT_SECRET` is set, ≥ 32 bytes, and **not** the dev default.
- [ ] `DB_PASSWORD` set; DB user has least privilege required.
- [ ] `CORS_ORIGINS` is the exact prod origin(s) — no wildcard.
- [ ] `COOKIE_SECURE=true` (set by the `prod` profile) and the app is behind TLS.
- [ ] `BACKUP_DIR` / `REPORT_DIR` point at mounted, backed-up volumes.
- [ ] Swagger/OpenAPI disabled in prod (default in `application-prod.yml`).
- [ ] Nginx `limit_req` configured on `/api/v1/auth/login` (brute-force control).

## 2. Backup before deploy

- [ ] Trigger a manual backup; wait for the `backup_job` row to reach `DONE`.
- [ ] Confirm the dump file exists in `BACKUP_DIR` with non-zero size.

## 3. Deploy

- [ ] Pull/build the release image; `docker compose -f docker-compose.prod.yml up -d`.
- [ ] Flyway applies pending migrations on boot — check logs for the applied range.
- [ ] `curl https://<host>/api/v1/health` → `200 {"status":"UP"}`.
- [ ] `GET /actuator/health` (authorized) → `UP` for db + disk.

## 4. Smoke test (critical flows)

- [ ] Log in via the UI as admin.
- [ ] View a known employee's time card (calendar + list).
- [ ] Submit and approve a leave request; confirm the time card recomputes.
- [ ] Run one report to `DONE` and download the CSV.
- [ ] Confirm an `audit_event` was written for an action just performed.

## 5. Observability

- [ ] `/actuator/prometheus` scraped; `timecard_recompute_seconds` and
      `ingestion_punches_total` present.
- [ ] Alerts from runbook §7 wired (recompute p95, ingestion rejects, backup
      failures, DB pool).

## 6. Post-deploy

- [ ] Tag the release in git (`vX.Y.Z`) and publish the changelog section.
- [ ] Rotate the bootstrap admin password if this is a first install.
- [ ] Record perf numbers from a staging run in [perf/README.md](../perf/README.md).

## Rollback

Migrations are forward-only. To roll back:

1. Redeploy the previous image.
2. **Restore the DB from the pre-deploy backup** (step 2) — required, because the
   new migrations may have altered schema the old image can't read.
3. Re-run the §4 smoke test on the restored stack.
4. File a post-mortem.

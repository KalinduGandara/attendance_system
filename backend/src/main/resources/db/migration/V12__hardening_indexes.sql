-- Phase 10 — Hardening: index review.
--
-- A review of the actual query patterns (repository @Query methods) against the
-- indexes shipped in earlier phases turned up three scans that hit no covering
-- index. Each addition below targets a specific, named query. The recompute
-- hot paths (punch lookup by employee+time, schedule/leave resolution, the
-- daily_time_card upsert key) are already covered and are left untouched.
--
-- All additive; no data change. Standard CREATE INDEX so it stays portable.

-- 1. punch_event retention purge — PunchEventRepository.findIdsOlderThan filters
--    on event_time_utc alone (WHERE event_time_utc < cutoff ORDER BY event_time_utc).
--    The existing ix_punch_event_employee_time leads with employee_id, so a
--    time-only predicate can't use it. Also serves the time-only punch search
--    (GET /punches with no employee filter).
CREATE INDEX ix_punch_event_time ON punch_event (event_time_utc);

-- 2. report_job retention purge — ReportJobRepository.findIdsOlderThan does
--    WHERE created_at < cutoff ORDER BY created_at, which had no index.
CREATE INDEX ix_report_job_created ON report_job (created_at);

-- 3. exception queue — ExceptionEventRepository.search drives the Exceptions
--    page, whose default view is "open exceptions, newest work_date first".
--    The existing (status, severity) index doesn't help the work_date ordering;
--    (status, work_date) does.
CREATE INDEX ix_exc_status_date ON exception_event (status, work_date);

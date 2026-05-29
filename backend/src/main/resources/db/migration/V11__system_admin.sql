-- Phase 9 — System Admin.
--
-- Tables:
--   system_setting     org-wide key/value config, type-aware. Drives the
--                      configurable backup / retention cron expressions.
--   backup_job         tracks each database backup run (scheduled or manual);
--                      a plain job-tracking table like report_job (not audited).
--   retention_policy   per-entity automatic-purge policy; audited config.
--
-- Permissions (audit.read, system.admin) were seeded in V2. No grants here.
-- JSON-typed setting values use TEXT per ADR 0003. Every table carries the
-- standard id/audit/version columns; `setting_key` and `entity_type` are
-- enforced UNIQUE for natural-key lookups.

SET @now = UTC_TIMESTAMP();

CREATE TABLE system_setting (
    id              BINARY(16) NOT NULL,
    setting_key     VARCHAR(64) NOT NULL,
    setting_value   TEXT NOT NULL,
    value_type      VARCHAR(16) NOT NULL,
    description     VARCHAR(255) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_setting_key (setting_key)
);

CREATE TABLE backup_job (
    id              BINARY(16) NOT NULL,
    trigger_type    VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    file_path       VARCHAR(500) NULL,
    size_bytes      BIGINT NULL,
    started_at      TIMESTAMP NOT NULL,
    completed_at    TIMESTAMP NULL,
    error_message   VARCHAR(1000) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_backup_job_status (status),
    KEY ix_backup_job_created (created_at)
);

CREATE TABLE retention_policy (
    id                  BINARY(16) NOT NULL,
    entity_type         VARCHAR(64) NOT NULL,
    retain_days         INT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    last_run_at         TIMESTAMP NULL,
    last_run_deleted    BIGINT NULL,
    created_at          TIMESTAMP NOT NULL,
    created_by          BINARY(16) NULL,
    updated_at          TIMESTAMP NOT NULL,
    updated_by          BINARY(16) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_policy_entity (entity_type)
);

-- ---------- seed org-wide settings ----------
-- cron settings drive the dynamic backup / retention schedulers; the remainder
-- are org configuration surfaced on the System Settings page.
INSERT INTO system_setting (id, setting_key, setting_value, value_type, description, created_at, updated_at, version) VALUES
    (UNHEX(REPLACE(UUID(),'-','')), 'org_name',              'Attendance Inc.', 'STRING',  'Display name of the organization', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'default_timezone',      'UTC',             'STRING',  'Fallback timezone for shift evaluation', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'week_start_day',        'MONDAY',          'STRING',  'First day of the working week', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'password_min_length',   '8',               'NUMBER',  'Minimum password length on change', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'backup_enabled',        'false',           'BOOLEAN', 'Whether scheduled database backups run', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'backup_cron',           '0 0 3 * * *',     'STRING',  'Spring cron for the scheduled backup job', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'backup_keep_count',     '14',              'NUMBER',  'Number of backup files to retain (0 = keep all)', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'retention_enabled',     'false',           'BOOLEAN', 'Whether the scheduled retention purge runs', @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'retention_cron',        '0 0 4 * * *',     'STRING',  'Spring cron for the scheduled retention purge', @now, @now, 0);

-- ---------- seed retention policies (all disabled by default) ----------
-- Audit retention is disabled by default and starts with the longest window
-- (forensic trail). Admins opt in per entity from the Retention page.
INSERT INTO retention_policy (id, entity_type, retain_days, enabled, created_at, updated_at, version) VALUES
    (UNHEX(REPLACE(UUID(),'-','')), 'punch_event', 365, FALSE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'audit_event', 730, FALSE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'report_job',   90, FALSE, @now, @now, 0);

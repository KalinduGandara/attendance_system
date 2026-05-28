-- Phase 5 — Punch ingestion + time-card engine.
--
-- Tables:
--   punch_event           raw events as ingested; immutable after persistence.
--   daily_time_card       upserted per (employee, work_date) by the engine.
--   time_card_breakdown   per-time-code minutes/rated breakdown for a day.
--   time_card_edit        append-only log of manual edits (endpoint lands in Phase 6).
--   exception_event       engine-emitted anomalies (resolution UI lands in Phase 7).
--
-- JSON-typed payload columns use TEXT (not the vendor-specific JSON type) for
-- portability per ADR 0003.
--
-- Permissions are already seeded by V2 (timecard.read, timecard.edit,
-- ingestion.write, exception.read, exception.resolve). No grants needed here.

CREATE TABLE punch_event (
    id                      BINARY(16) NOT NULL,
    employee_id             BINARY(16) NULL,
    device_id               BINARY(16) NULL,
    ingestion_source_id     BINARY(16) NOT NULL,
    external_event_id       VARCHAR(128) NOT NULL,
    event_type              VARCHAR(16) NOT NULL,
    event_time_utc          TIMESTAMP NOT NULL,
    credential_value_hash   CHAR(64) NULL,
    raw_payload_json        TEXT NULL,
    status                  VARCHAR(16) NOT NULL,
    processed_at            TIMESTAMP NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_punch_event_idempotency (ingestion_source_id, external_event_id),
    KEY ix_punch_event_employee_time (employee_id, event_time_utc),
    KEY ix_punch_event_status (status),
    CONSTRAINT fk_punch_event_source   FOREIGN KEY (ingestion_source_id) REFERENCES ingestion_source (id),
    CONSTRAINT fk_punch_event_device   FOREIGN KEY (device_id)           REFERENCES device (id),
    CONSTRAINT fk_punch_event_employee FOREIGN KEY (employee_id)         REFERENCES employee (id)
);

CREATE TABLE daily_time_card (
    id                      BINARY(16) NOT NULL,
    employee_id             BINARY(16) NOT NULL,
    work_date               DATE NOT NULL,
    computed_at             TIMESTAMP NOT NULL,
    resolved_shift_id       BINARY(16) NULL,
    scheduled_start_utc     TIMESTAMP NULL,
    scheduled_end_utc       TIMESTAMP NULL,
    actual_start_utc        TIMESTAMP NULL,
    actual_end_utc          TIMESTAMP NULL,
    worked_minutes          INT NOT NULL DEFAULT 0,
    break_minutes           INT NOT NULL DEFAULT 0,
    overtime_minutes        INT NOT NULL DEFAULT 0,
    late_minutes            INT NOT NULL DEFAULT 0,
    early_out_minutes       INT NOT NULL DEFAULT 0,
    status                  VARCHAR(16) NOT NULL,
    notes                   VARCHAR(500) NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_time_card_employee_date (employee_id, work_date),
    KEY ix_daily_time_card_date (work_date),
    CONSTRAINT fk_dtc_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtc_shift    FOREIGN KEY (resolved_shift_id) REFERENCES shift (id)
);

CREATE TABLE time_card_breakdown (
    id                      BINARY(16) NOT NULL,
    daily_time_card_id      BINARY(16) NOT NULL,
    time_code_id            BINARY(16) NOT NULL,
    minutes                 INT NOT NULL,
    rated_minutes           INT NOT NULL,
    sequence_order          INT NOT NULL,
    PRIMARY KEY (id),
    KEY ix_tcb_card (daily_time_card_id),
    KEY ix_tcb_timecode (time_code_id),
    CONSTRAINT fk_tcb_card     FOREIGN KEY (daily_time_card_id) REFERENCES daily_time_card (id) ON DELETE CASCADE,
    CONSTRAINT fk_tcb_timecode FOREIGN KEY (time_code_id)       REFERENCES time_code (id)
);

CREATE TABLE time_card_edit (
    id                      BINARY(16) NOT NULL,
    daily_time_card_id      BINARY(16) NOT NULL,
    punch_event_id          BINARY(16) NULL,
    change_type             VARCHAR(16) NOT NULL,
    before_json             TEXT NULL,
    after_json              TEXT NULL,
    reason                  VARCHAR(500) NOT NULL,
    edited_by_user_id       BINARY(16) NOT NULL,
    edited_at               TIMESTAMP NOT NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_tce_card (daily_time_card_id),
    KEY ix_tce_punch (punch_event_id),
    CONSTRAINT fk_tce_card  FOREIGN KEY (daily_time_card_id) REFERENCES daily_time_card (id) ON DELETE CASCADE,
    CONSTRAINT fk_tce_punch FOREIGN KEY (punch_event_id)     REFERENCES punch_event (id),
    CONSTRAINT fk_tce_user  FOREIGN KEY (edited_by_user_id)  REFERENCES user (id)
);

CREATE TABLE exception_event (
    id                      BINARY(16) NOT NULL,
    employee_id             BINARY(16) NOT NULL,
    work_date               DATE NOT NULL,
    daily_time_card_id      BINARY(16) NULL,
    exception_type          VARCHAR(32) NOT NULL,
    severity                VARCHAR(16) NOT NULL,
    details_json            TEXT NULL,
    status                  VARCHAR(16) NOT NULL,
    resolved_by             BINARY(16) NULL,
    resolved_at             TIMESTAMP NULL,
    resolution_note         VARCHAR(500) NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_exc_employee_date (employee_id, work_date),
    KEY ix_exc_status_severity (status, severity),
    CONSTRAINT fk_exc_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_exc_card     FOREIGN KEY (daily_time_card_id) REFERENCES daily_time_card (id) ON DELETE SET NULL,
    CONSTRAINT fk_exc_user     FOREIGN KEY (resolved_by) REFERENCES user (id)
);

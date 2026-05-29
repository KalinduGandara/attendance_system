-- Phase 8 — Reporting.
--
-- A `report_job` tracks the asynchronous generation of one of the seven SRS
-- §3.6 report types. The request parameters (date range, filters, sort,
-- custom-field selection) are stored as JSON so the job is self-describing and
-- survives a backend restart (state lives in the table, not in memory). The
-- generated CSV is written under the `reports/` directory and `file_path`
-- points at it for the download endpoint.
--
-- Permission seeded in V2 (report.run). No grants needed here.
-- JSON columns are TEXT per ADR 0003.

CREATE TABLE report_job (
    id                      BINARY(16) NOT NULL,
    report_type             VARCHAR(32) NOT NULL,
    parameters_json         TEXT NOT NULL,
    requested_by            BINARY(16) NULL,
    status                  VARCHAR(16) NOT NULL,
    file_path               VARCHAR(500) NULL,
    row_count               BIGINT NULL,
    started_at              TIMESTAMP NULL,
    completed_at            TIMESTAMP NULL,
    error_message           VARCHAR(1000) NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_report_job_status (status),
    KEY ix_report_job_requested_by (requested_by)
);

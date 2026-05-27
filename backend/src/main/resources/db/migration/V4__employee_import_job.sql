-- Job tracking for bulk employee CSV imports. Lays the pattern for the broader
-- report_job table coming in Phase 8.

CREATE TABLE employee_import_job (
    id              BINARY(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    file_name       VARCHAR(255) NULL,
    total_rows      INT NOT NULL DEFAULT 0,
    processed_rows  INT NOT NULL DEFAULT 0,
    created_count   INT NOT NULL DEFAULT 0,
    updated_count   INT NOT NULL DEFAULT 0,
    error_count     INT NOT NULL DEFAULT 0,
    error_report    TEXT NULL,
    requested_by    BINARY(16) NULL,
    started_at      TIMESTAMP NULL,
    completed_at    TIMESTAMP NULL,
    error_message   VARCHAR(1000) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_employee_import_status (status)
);

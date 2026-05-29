-- Phase 7 — Leave types, balances, requests.
--
-- A `leave_type` is operator-defined and references a LEAVE-category time code
-- (the rate on that code determines how much the day is paid). `leave_balance`
-- carries the per-employee, per-year remaining balance. `leave_request` is the
-- request lifecycle (PENDING → APPROVED/REJECTED/CANCELLED), with a flag for
-- retroactive entries created via the time-card UI deep-link.
--
-- Permissions seeded in V2 (leave.read, leave.approve). No grants needed here.
--
-- JSON columns are TEXT per ADR 0003.

CREATE TABLE leave_type (
    id                      BINARY(16) NOT NULL,
    time_code_id            BINARY(16) NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    default_annual_days     DECIMAL(5,2) NOT NULL,
    requires_approval       BOOLEAN NOT NULL,
    accrual_rule_json       TEXT NULL,
    active                  BOOLEAN NOT NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_type_name (name),
    CONSTRAINT fk_leave_type_timecode FOREIGN KEY (time_code_id) REFERENCES time_code (id)
);

CREATE TABLE leave_balance (
    id                      BINARY(16) NOT NULL,
    employee_id             BINARY(16) NOT NULL,
    leave_type_id           BINARY(16) NOT NULL,
    balance_year            INT NOT NULL,
    balance_days            DECIMAL(5,2) NOT NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_balance_emp_type_year (employee_id, leave_type_id, balance_year),
    CONSTRAINT fk_leave_balance_employee FOREIGN KEY (employee_id)    REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_leave_balance_type     FOREIGN KEY (leave_type_id)  REFERENCES leave_type (id)
);

CREATE TABLE leave_request (
    id                      BINARY(16) NOT NULL,
    employee_id             BINARY(16) NOT NULL,
    leave_type_id           BINARY(16) NOT NULL,
    start_date              DATE NOT NULL,
    end_date                DATE NOT NULL,
    half_day                BOOLEAN NOT NULL DEFAULT FALSE,
    half_day_part           VARCHAR(16) NULL,
    reason                  VARCHAR(500) NULL,
    status                  VARCHAR(16) NOT NULL,
    retroactive             BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by             BINARY(16) NULL,
    approved_at             TIMESTAMP NULL,
    rejection_reason        VARCHAR(500) NULL,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_leave_request_employee_date (employee_id, start_date),
    KEY ix_leave_request_status (status),
    CONSTRAINT fk_leave_request_employee FOREIGN KEY (employee_id)   REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_leave_request_type     FOREIGN KEY (leave_type_id) REFERENCES leave_type (id),
    CONSTRAINT fk_leave_request_approver FOREIGN KEY (approved_by)   REFERENCES user (id),
    CONSTRAINT ck_leave_request_date_order CHECK (end_date >= start_date)
);

-- Time codes (classifications of worked time: attendance, overtime, leave) and
-- shift configuration with nested rules.
--
-- Children of `shift` (segments, rounding, grace, breaks, OT tiers, floating
-- candidates) are managed as atomic full-replacement collections from the API.
-- Each child carries `shift_id` with ON DELETE CASCADE so removing a shift
-- cleans up the entire tree in one statement.

CREATE TABLE time_code (
    id                      BINARY(16) NOT NULL,
    code                    VARCHAR(32) NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    category                VARCHAR(16) NOT NULL,
    rate                    DECIMAL(4,2) NOT NULL,
    color                   CHAR(7) NOT NULL,
    paid                    BOOLEAN NOT NULL,
    counts_for_attendance   BOOLEAN NOT NULL,
    description             VARCHAR(255) NULL,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL,
    created_by              BINARY(16) NULL,
    updated_at              TIMESTAMP NOT NULL,
    updated_by              BINARY(16) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_time_code_code (code),
    KEY ix_time_code_category (category),
    KEY ix_time_code_active (active),
    CONSTRAINT ck_time_code_rate CHECK (rate BETWEEN 0 AND 10)
);

CREATE TABLE shift (
    id                          BINARY(16) NOT NULL,
    name                        VARCHAR(128) NOT NULL,
    shift_type                  VARCHAR(16) NOT NULL,
    color                       CHAR(7) NOT NULL,
    timezone                    VARCHAR(64) NULL,
    description                 VARCHAR(255) NULL,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    attendance_time_code_id     BINARY(16) NOT NULL,
    created_at                  TIMESTAMP NOT NULL,
    created_by                  BINARY(16) NULL,
    updated_at                  TIMESTAMP NOT NULL,
    updated_by                  BINARY(16) NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_shift_type (shift_type),
    KEY ix_shift_active (active),
    CONSTRAINT fk_shift_attendance_time_code FOREIGN KEY (attendance_time_code_id) REFERENCES time_code (id)
);

CREATE TABLE shift_segment (
    id                          BINARY(16) NOT NULL,
    shift_id                    BINARY(16) NOT NULL,
    segment_order               INT NOT NULL,
    start_minute_of_day         INT NOT NULL,
    end_minute_of_day           INT NOT NULL,
    required_minutes            INT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shift_segment_order (shift_id, segment_order),
    CONSTRAINT fk_shift_segment_shift FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

CREATE TABLE shift_rounding_rule (
    id                          BINARY(16) NOT NULL,
    shift_id                    BINARY(16) NOT NULL,
    kind                        VARCHAR(16) NOT NULL,
    unit_minutes                INT NOT NULL,
    mode                        VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_shift_rounding_shift (shift_id),
    CONSTRAINT fk_shift_rounding_shift FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

CREATE TABLE shift_grace_rule (
    id                          BINARY(16) NOT NULL,
    shift_id                    BINARY(16) NOT NULL,
    kind                        VARCHAR(16) NOT NULL,
    minutes                     INT NOT NULL,
    PRIMARY KEY (id),
    KEY ix_shift_grace_shift (shift_id),
    CONSTRAINT fk_shift_grace_shift FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

CREATE TABLE break_rule (
    id                          BINARY(16) NOT NULL,
    shift_id                    BINARY(16) NOT NULL,
    name                        VARCHAR(64) NOT NULL,
    kind                        VARCHAR(16) NOT NULL,
    duration_minutes            INT NOT NULL,
    earliest_start_minute       INT NULL,
    after_hours_worked          INT NULL,
    paid                        BOOLEAN NOT NULL,
    time_code_id                BINARY(16) NULL,
    PRIMARY KEY (id),
    KEY ix_break_rule_shift (shift_id),
    CONSTRAINT fk_break_rule_shift FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_break_rule_time_code FOREIGN KEY (time_code_id) REFERENCES time_code (id)
);

CREATE TABLE overtime_rule (
    id                          BINARY(16) NOT NULL,
    shift_id                    BINARY(16) NOT NULL,
    sequence_order              INT NOT NULL,
    after_minutes_worked        INT NOT NULL,
    time_code_id                BINARY(16) NOT NULL,
    max_minutes                 INT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_overtime_rule_order (shift_id, sequence_order),
    CONSTRAINT fk_overtime_rule_shift FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_overtime_rule_time_code FOREIGN KEY (time_code_id) REFERENCES time_code (id)
);

CREATE TABLE floating_shift_candidate (
    floating_shift_id           BINARY(16) NOT NULL,
    candidate_shift_id          BINARY(16) NOT NULL,
    PRIMARY KEY (floating_shift_id, candidate_shift_id),
    KEY ix_floating_candidate_candidate (candidate_shift_id),
    CONSTRAINT fk_floating_shift     FOREIGN KEY (floating_shift_id)  REFERENCES shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_floating_candidate FOREIGN KEY (candidate_shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

-- Permissions for Phase 3 (time codes + shifts).
SET @now = UTC_TIMESTAMP();
INSERT INTO permission (id, code, description, created_at, updated_at, version) VALUES
    (UNHEX(REPLACE(UUID(),'-','')), 'timecode.read',  'View time codes',           @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'timecode.write', 'Create / edit time codes',  @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'shift.read',     'View shifts',               @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'shift.write',    'Create / edit shifts',      @now, @now, 0);

-- Grant new permissions to existing roles. ADMIN gets all; HR_MANAGER manages
-- the configuration; MANAGER and EMPLOYEE can only read.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.name = 'ADMIN'
   AND p.code IN ('timecode.read','timecode.write','shift.read','shift.write');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.name = 'HR_MANAGER'
   AND p.code IN ('timecode.read','timecode.write','shift.read','shift.write');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.name = 'MANAGER'
   AND p.code IN ('timecode.read','shift.read');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.name = 'EMPLOYEE'
   AND p.code IN ('timecode.read','shift.read');

-- Seed canonical time codes used by every fresh install. Codes are stable; the
-- admin can rename / recolor / disable in the UI. Rates are chosen so multiplying
-- minutes-by-rate yields the conventional pay multiplier (1.0 reg, 1.5 OT-A, 2.0 OT-B).
INSERT INTO time_code (id, code, name, category, rate, color, paid, counts_for_attendance,
                       description, active, created_at, updated_at, version) VALUES
    (UNHEX(REPLACE(UUID(),'-','')), 'REG',  'Regular hours',     'ATTENDANCE', 1.00, '#3b82f6', TRUE,  TRUE,
        'Standard worked time at base rate.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'OT-A', 'Overtime tier A',   'OVERTIME',   1.50, '#f59e0b', TRUE,  TRUE,
        'First overtime tier, typically 1.5x.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'OT-B', 'Overtime tier B',   'OVERTIME',   2.00, '#ef4444', TRUE,  TRUE,
        'Second overtime tier, typically 2.0x.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'BRK',  'Paid break',        'ATTENDANCE', 1.00, '#a3a3a3', TRUE,  FALSE,
        'Paid break time for tracked breaks.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'HOL',  'Holiday',           'ATTENDANCE', 1.00, '#10b981', TRUE,  FALSE,
        'Paid public holiday.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'ANN',  'Annual leave',      'LEAVE',      1.00, '#8b5cf6', TRUE,  FALSE,
        'Annual / vacation leave.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'SICK', 'Sick leave',        'LEAVE',      1.00, '#ec4899', TRUE,  FALSE,
        'Sick leave.', TRUE, @now, @now, 0),
    (UNHEX(REPLACE(UUID(),'-','')), 'UNP',  'Unpaid leave',      'LEAVE',      0.00, '#6b7280', FALSE, FALSE,
        'Approved unpaid absence.', TRUE, @now, @now, 0);

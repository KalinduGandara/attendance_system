-- Schedule module: reusable shift cycles (templates), assignments of templates
-- to employees or groups over date ranges, and per-employee temporary overrides
-- that beat any template.
--
-- Resolution priority is enforced in code (com.attendance.schedule.service.ScheduleResolver):
--     temporary > employee-assignment > group-assignment > none
--
-- `schedule.read` / `schedule.write` permissions are already seeded by V2 and
-- granted to ADMIN / HR_MANAGER / MANAGER / EMPLOYEE; this migration only adds
-- the missing read grant for EMPLOYEE so staff can see their own roster.

CREATE TABLE schedule_template (
    id                  BINARY(16) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    cycle_type          VARCHAR(16) NOT NULL,
    cycle_length_days   INT NOT NULL,
    description         VARCHAR(255) NULL,
    created_at          TIMESTAMP NOT NULL,
    created_by          BINARY(16) NULL,
    updated_at          TIMESTAMP NOT NULL,
    updated_by          BINARY(16) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_template_name (name),
    CONSTRAINT ck_schedule_template_cycle_length CHECK (cycle_length_days BETWEEN 1 AND 366)
);

CREATE TABLE schedule_template_day (
    id              BINARY(16) NOT NULL,
    template_id     BINARY(16) NOT NULL,
    day_index       INT NOT NULL,
    shift_id        BINARY(16) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_template_day (template_id, day_index),
    KEY ix_schedule_template_day_shift (shift_id),
    CONSTRAINT fk_std_template FOREIGN KEY (template_id) REFERENCES schedule_template (id) ON DELETE CASCADE,
    CONSTRAINT fk_std_shift    FOREIGN KEY (shift_id)    REFERENCES shift (id)
);

CREATE TABLE schedule_assignment (
    id              BINARY(16) NOT NULL,
    target_type     VARCHAR(16) NOT NULL,
    target_id       BINARY(16) NOT NULL,
    template_id     BINARY(16) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NULL,
    priority        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_schedule_assignment_target (target_type, target_id, start_date),
    KEY ix_schedule_assignment_template (template_id),
    CONSTRAINT fk_schedule_assignment_template FOREIGN KEY (template_id) REFERENCES schedule_template (id)
);

CREATE TABLE temporary_schedule (
    id              BINARY(16) NOT NULL,
    employee_id     BINARY(16) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    shift_id        BINARY(16) NULL,
    reason          VARCHAR(255) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_temporary_schedule_employee (employee_id, start_date),
    CONSTRAINT fk_temporary_schedule_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_temporary_schedule_shift    FOREIGN KEY (shift_id)    REFERENCES shift (id),
    CONSTRAINT ck_temporary_schedule_range CHECK (end_date >= start_date)
);

-- V2 already seeded schedule.read / schedule.write and granted them as follows:
--   ADMIN, HR_MANAGER  → read + write
--   MANAGER            → read
--   EMPLOYEE           → (none)
-- Employees need to see their own roster on the calendar, so grant read here.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.name = 'EMPLOYEE'
   AND p.code = 'schedule.read'
   AND NOT EXISTS (
       SELECT 1 FROM role_permission rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
   );

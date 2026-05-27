-- Organization module: departments, groups, employees, custom fields, holidays.
-- JSON-typed columns are stored as TEXT for portability; the dialect port handles
-- vendor-specific JSON access. All FKs reference identity / organization tables only.

CREATE TABLE department (
    id              BINARY(16) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    parent_id       BINARY(16) NULL,
    timezone        VARCHAR(64) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_department_parent (parent_id),
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES department (id) ON DELETE SET NULL
);

CREATE TABLE user_group (
    id              BINARY(16) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    parent_id       BINARY(16) NULL,
    description     VARCHAR(255) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_user_group_parent (parent_id),
    CONSTRAINT fk_user_group_parent FOREIGN KEY (parent_id) REFERENCES user_group (id) ON DELETE SET NULL
);

CREATE TABLE employee (
    id                BINARY(16) NOT NULL,
    user_id           BINARY(16) NULL,
    employee_code     VARCHAR(64) NOT NULL,
    first_name        VARCHAR(64) NOT NULL,
    last_name         VARCHAR(64) NOT NULL,
    email             VARCHAR(255) NULL,
    phone             VARCHAR(32) NULL,
    department_id     BINARY(16) NULL,
    manager_id        BINARY(16) NULL,
    employment_type   VARCHAR(16) NOT NULL,
    hire_date         DATE NOT NULL,
    termination_date  DATE NULL,
    timezone          VARCHAR(64) NULL,
    status            VARCHAR(16) NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    created_by        BINARY(16) NULL,
    updated_at        TIMESTAMP NOT NULL,
    updated_by        BINARY(16) NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_code (employee_code),
    UNIQUE KEY uk_employee_user (user_id),
    KEY ix_employee_department (department_id),
    KEY ix_employee_manager (manager_id),
    KEY ix_employee_status (status),
    CONSTRAINT fk_employee_user       FOREIGN KEY (user_id)       REFERENCES `user` (id)     ON DELETE SET NULL,
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department (id) ON DELETE SET NULL,
    CONSTRAINT fk_employee_manager    FOREIGN KEY (manager_id)    REFERENCES employee (id)   ON DELETE SET NULL
);

CREATE TABLE employee_group (
    employee_id     BINARY(16) NOT NULL,
    group_id        BINARY(16) NOT NULL,
    PRIMARY KEY (employee_id, group_id),
    KEY ix_employee_group_group (group_id),
    CONSTRAINT fk_eg_employee FOREIGN KEY (employee_id) REFERENCES employee (id)   ON DELETE CASCADE,
    CONSTRAINT fk_eg_group    FOREIGN KEY (group_id)    REFERENCES user_group (id) ON DELETE CASCADE
);

CREATE TABLE custom_field_definition (
    id              BINARY(16) NOT NULL,
    entity_type     VARCHAR(32) NOT NULL,
    field_key       VARCHAR(64) NOT NULL,
    display_label   VARCHAR(128) NOT NULL,
    field_type      VARCHAR(16) NOT NULL,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    options_json    TEXT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_custom_field_def_key (entity_type, field_key)
);

CREATE TABLE custom_field_value (
    id              BINARY(16) NOT NULL,
    definition_id   BINARY(16) NOT NULL,
    entity_id       BINARY(16) NOT NULL,
    value_string    TEXT NULL,
    value_number    DECIMAL(20,6) NULL,
    value_date      DATE NULL,
    value_boolean   BOOLEAN NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_custom_field_value (definition_id, entity_id),
    KEY ix_custom_field_value_entity (entity_id),
    CONSTRAINT fk_cfv_definition FOREIGN KEY (definition_id) REFERENCES custom_field_definition (id) ON DELETE CASCADE
);

CREATE TABLE holiday (
    id                  BINARY(16) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    holiday_date        DATE NOT NULL,
    recurring_yearly    BOOLEAN NOT NULL DEFAULT FALSE,
    paid                BOOLEAN NOT NULL DEFAULT TRUE,
    description         VARCHAR(255) NULL,
    created_at          TIMESTAMP NOT NULL,
    created_by          BINARY(16) NULL,
    updated_at          TIMESTAMP NOT NULL,
    updated_by          BINARY(16) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_holiday_date (holiday_date)
);

CREATE TABLE holiday_group (
    holiday_id      BINARY(16) NOT NULL,
    group_id        BINARY(16) NOT NULL,
    PRIMARY KEY (holiday_id, group_id),
    KEY ix_holiday_group_group (group_id),
    CONSTRAINT fk_hg_holiday FOREIGN KEY (holiday_id) REFERENCES holiday (id)    ON DELETE CASCADE,
    CONSTRAINT fk_hg_group   FOREIGN KEY (group_id)   REFERENCES user_group (id) ON DELETE CASCADE
);

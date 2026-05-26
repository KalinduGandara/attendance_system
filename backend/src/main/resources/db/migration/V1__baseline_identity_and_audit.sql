-- Baseline migration: identity + audit tables.
-- Portable ANSI SQL where practical; MariaDB-targeted types declared explicitly.

CREATE TABLE `user` (
    id              BINARY(16) NOT NULL,
    username        VARCHAR(64) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(72) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    display_name    VARCHAR(128) NULL,
    last_login_at   TIMESTAMP NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email)
);

CREATE TABLE role (
    id              BINARY(16) NOT NULL,
    name            VARCHAR(64) NOT NULL,
    description     VARCHAR(255) NULL,
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_name (name)
);

CREATE TABLE permission (
    id              BINARY(16) NOT NULL,
    code            VARCHAR(64) NOT NULL,
    description     VARCHAR(255) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code)
);

CREATE TABLE role_permission (
    role_id         BINARY(16) NOT NULL,
    permission_id   BINARY(16) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role        FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission  FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
);

CREATE TABLE user_role (
    user_id         BINARY(16) NOT NULL,
    role_id         BINARY(16) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE
);

CREATE TABLE refresh_token (
    id              BINARY(16) NOT NULL,
    user_id         BINARY(16) NOT NULL,
    token_hash      CHAR(64) NOT NULL,
    family_id       BINARY(16) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    revoked_at      TIMESTAMP NULL,
    user_agent      VARCHAR(255) NULL,
    ip              VARCHAR(45) NULL,
    created_at      TIMESTAMP NOT NULL,
    created_by      BINARY(16) NULL,
    updated_at      TIMESTAMP NOT NULL,
    updated_by      BINARY(16) NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY ix_refresh_token_user_revoked (user_id, revoked_at),
    KEY ix_refresh_token_family (family_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
);

CREATE TABLE audit_event (
    id              BINARY(16) NOT NULL,
    actor_user_id   BINARY(16) NULL,
    actor_username  VARCHAR(64) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NULL,
    entity_id       BINARY(16) NULL,
    before_json     TEXT NULL,
    after_json      TEXT NULL,
    ip              VARCHAR(45) NULL,
    user_agent      VARCHAR(255) NULL,
    request_id      CHAR(36) NULL,
    occurred_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_occurred_at (occurred_at),
    KEY ix_audit_actor (actor_user_id, occurred_at),
    KEY ix_audit_entity (entity_type, entity_id)
);

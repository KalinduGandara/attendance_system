-- Device, ingestion source, credential tables.
--
-- All JSON-typed columns are TEXT for portability; the dialect port handles
-- vendor-specific JSON access. Credential resolution uses a fixed-width SHA-256
-- digest (credential_lookup) so an ingest path can resolve an employee with a
-- single indexed equality probe.

CREATE TABLE device (
    id                  BINARY(16) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    device_type         VARCHAR(16) NOT NULL,
    location            VARCHAR(255) NULL,
    status              VARCHAR(16) NOT NULL,
    capabilities_json   TEXT NOT NULL,
    last_seen_at        TIMESTAMP NULL,
    created_at          TIMESTAMP NOT NULL,
    created_by          BINARY(16) NULL,
    updated_at          TIMESTAMP NOT NULL,
    updated_by          BINARY(16) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_device_status (status)
);

CREATE TABLE ingestion_source (
    id                  BINARY(16) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    source_type         VARCHAR(16) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    config_json         TEXT NOT NULL,
    api_key_hash        CHAR(64) NULL,
    last_event_at       TIMESTAMP NULL,
    events_total        BIGINT NOT NULL DEFAULT 0,
    events_rejected     BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    created_by          BINARY(16) NULL,
    updated_at          TIMESTAMP NOT NULL,
    updated_by          BINARY(16) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingestion_source_api_key (api_key_hash),
    KEY ix_ingestion_source_enabled (enabled)
);

CREATE TABLE device_ingestion_source (
    device_id           BINARY(16) NOT NULL,
    ingestion_source_id BINARY(16) NOT NULL,
    PRIMARY KEY (device_id, ingestion_source_id),
    KEY ix_dis_source (ingestion_source_id),
    CONSTRAINT fk_dis_device FOREIGN KEY (device_id) REFERENCES device (id)          ON DELETE CASCADE,
    CONSTRAINT fk_dis_source FOREIGN KEY (ingestion_source_id) REFERENCES ingestion_source (id) ON DELETE CASCADE
);

CREATE TABLE credential (
    id                  BINARY(16) NOT NULL,
    employee_id         BINARY(16) NOT NULL,
    credential_type     VARCHAR(16) NOT NULL,
    credential_value    VARCHAR(255) NOT NULL,
    credential_lookup   CHAR(64) NOT NULL,
    valid_from          DATE NOT NULL,
    valid_to            DATE NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    created_by          BINARY(16) NULL,
    updated_at          TIMESTAMP NOT NULL,
    updated_by          BINARY(16) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_lookup (credential_type, credential_lookup),
    KEY ix_credential_employee (employee_id),
    KEY ix_credential_status (status),
    CONSTRAINT fk_credential_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE
);

-- Seed a single REST ingestion source so the system is immediately usable for
-- JWT-authenticated punch submission. Admin must rotate-key on it to enable
-- X-Source-Api-Key auth (the key is shown once on rotation).
SET @now = UTC_TIMESTAMP();
INSERT INTO ingestion_source (id, name, source_type, enabled, config_json,
                              api_key_hash, events_total, events_rejected,
                              created_at, updated_at, version)
VALUES (UNHEX(REPLACE(UUID(), '-', '')),
        'Default Web Ingestion', 'REST', TRUE, '{}',
        NULL, 0, 0,
        @now, @now, 0);

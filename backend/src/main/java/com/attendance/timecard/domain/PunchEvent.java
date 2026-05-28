package com.attendance.timecard.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "punch_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_punch_event_idempotency",
                columnNames = {"ingestion_source_id", "external_event_id"}))
@Getter
@Setter
@NoArgsConstructor
@Auditable("PunchEvent")
@EntityListeners(AuditEntityListener.class)
public class PunchEvent extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "employee_id", columnDefinition = "BINARY(16)")
    private UUID employeeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "device_id", columnDefinition = "BINARY(16)")
    private UUID deviceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ingestion_source_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID ingestionSourceId;

    @Column(name = "external_event_id", nullable = false, length = 128)
    private String externalEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private PunchEventType eventType;

    @Column(name = "event_time_utc", nullable = false)
    private Instant eventTimeUtc;

    @Column(name = "credential_value_hash", columnDefinition = "CHAR(64)")
    private String credentialValueHash;

    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    private String rawPayloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PunchEventStatus status;

    @Column(name = "processed_at")
    private Instant processedAt;
}

package com.attendance.device.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ingestion_source")
@Getter
@Setter
@NoArgsConstructor
@Auditable("IngestionSource")
@EntityListeners(AuditEntityListener.class)
public class IngestionSource extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private IngestionSourceType sourceType = IngestionSourceType.REST;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson = "{}";

    @Column(name = "api_key_hash", columnDefinition = "CHAR(64)")
    private String apiKeyHash;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "events_total", nullable = false)
    private long eventsTotal;

    @Column(name = "events_rejected", nullable = false)
    private long eventsRejected;
}

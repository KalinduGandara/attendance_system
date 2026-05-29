package com.attendance.admin.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Automatic-purge policy for one entity type (e.g. {@code punch_event}). The
 * scheduled retention job deletes rows older than {@code retainDays} when
 * {@code enabled}. Audited so opting in / changing the window leaves a trail.
 */
@Entity
@Table(name = "retention_policy")
@Getter
@Setter
@NoArgsConstructor
@Auditable("RetentionPolicy")
@EntityListeners(AuditEntityListener.class)
public class RetentionPolicy extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private String entityType;

    @Column(name = "retain_days", nullable = false)
    private int retainDays;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_deleted")
    private Long lastRunDeleted;
}

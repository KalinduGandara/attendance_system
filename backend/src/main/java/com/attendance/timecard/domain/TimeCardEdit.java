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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only log of manual edits made via the time-card edit endpoint
 * (the endpoint itself lands in Phase 6; the table exists in Phase 5 so the
 * recompute service can begin populating it without a follow-up migration).
 */
@Entity
@Table(name = "time_card_edit")
@Getter
@Setter
@NoArgsConstructor
@Auditable("TimeCardEdit")
@EntityListeners(AuditEntityListener.class)
public class TimeCardEdit extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "daily_time_card_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID dailyTimeCardId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "punch_event_id", columnDefinition = "BINARY(16)")
    private UUID punchEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 16)
    private TimeCardEditChangeType changeType;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "edited_by_user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID editedByUserId;

    @Column(name = "edited_at", nullable = false)
    private Instant editedAt;
}

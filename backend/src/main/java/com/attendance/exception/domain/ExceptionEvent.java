package com.attendance.exception.domain;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exception_event")
@Getter
@Setter
@NoArgsConstructor
@Auditable("ExceptionEvent")
@EntityListeners(AuditEntityListener.class)
public class ExceptionEvent extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "employee_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "daily_time_card_id", columnDefinition = "BINARY(16)")
    private UUID dailyTimeCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 32)
    private ExceptionType exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private ExceptionSeverity severity;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_by", columnDefinition = "BINARY(16)")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;
}

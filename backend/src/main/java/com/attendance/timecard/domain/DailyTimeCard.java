package com.attendance.timecard.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "daily_time_card",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_time_card_employee_date",
                columnNames = {"employee_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
@Auditable("DailyTimeCard")
@EntityListeners(AuditEntityListener.class)
public class DailyTimeCard extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "employee_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_shift_id", columnDefinition = "BINARY(16)")
    private UUID resolvedShiftId;

    @Column(name = "scheduled_start_utc")
    private Instant scheduledStartUtc;

    @Column(name = "scheduled_end_utc")
    private Instant scheduledEndUtc;

    @Column(name = "actual_start_utc")
    private Instant actualStartUtc;

    @Column(name = "actual_end_utc")
    private Instant actualEndUtc;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    @Column(name = "early_out_minutes", nullable = false)
    private int earlyOutMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DailyTimeCardStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "dailyTimeCard", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sequenceOrder ASC")
    private List<TimeCardBreakdown> breakdowns = new ArrayList<>();
}

package com.attendance.schedule.domain;

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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "schedule_assignment")
@Getter
@Setter
@NoArgsConstructor
@Auditable("ScheduleAssignment")
@EntityListeners(AuditEntityListener.class)
public class ScheduleAssignment extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private AssignmentTargetType targetType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "target_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "template_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID templateId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "priority", nullable = false)
    private int priority;
}

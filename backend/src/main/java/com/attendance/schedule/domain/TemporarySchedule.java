package com.attendance.schedule.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "temporary_schedule")
@Getter
@Setter
@NoArgsConstructor
@Auditable("TemporarySchedule")
@EntityListeners(AuditEntityListener.class)
public class TemporarySchedule extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "employee_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID employeeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shift_id", columnDefinition = "BINARY(16)")
    private UUID shiftId;

    @Column(name = "reason", length = 255)
    private String reason;
}

package com.attendance.leave.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "leave_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_balance_emp_type_year",
                columnNames = {"employee_id", "leave_type_id", "balance_year"}))
@Getter
@Setter
@NoArgsConstructor
@Auditable("LeaveBalance")
@EntityListeners(AuditEntityListener.class)
public class LeaveBalance extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "employee_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID employeeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "leave_type_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "balance_year", nullable = false)
    private int year;

    @Column(name = "balance_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal balanceDays;
}

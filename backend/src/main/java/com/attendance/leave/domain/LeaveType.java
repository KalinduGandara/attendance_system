package com.attendance.leave.domain;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "leave_type")
@Getter
@Setter
@NoArgsConstructor
@Auditable("LeaveType")
@EntityListeners(AuditEntityListener.class)
public class LeaveType extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "time_code_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID timeCodeId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "default_annual_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultAnnualDays;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "accrual_rule_json", columnDefinition = "TEXT")
    private String accrualRuleJson;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}

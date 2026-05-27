package com.attendance.shift.domain;

import com.attendance.common.jpa.BaseChildEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "overtime_rule")
@Getter
@Setter
@NoArgsConstructor
public class OvertimeRule extends BaseChildEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_overtime_rule_shift"))
    private Shift shift;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "after_minutes_worked", nullable = false)
    private int afterMinutesWorked;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "time_code_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID timeCodeId;

    @Column(name = "max_minutes")
    private Integer maxMinutes;
}

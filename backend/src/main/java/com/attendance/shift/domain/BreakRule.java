package com.attendance.shift.domain;

import com.attendance.common.jpa.BaseChildEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "break_rule")
@Getter
@Setter
@NoArgsConstructor
public class BreakRule extends BaseChildEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_break_rule_shift"))
    private Shift shift;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private BreakKind kind;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "earliest_start_minute")
    private Integer earliestStartMinute;

    @Column(name = "after_hours_worked")
    private Integer afterHoursWorked;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "time_code_id", columnDefinition = "BINARY(16)")
    private UUID timeCodeId;
}

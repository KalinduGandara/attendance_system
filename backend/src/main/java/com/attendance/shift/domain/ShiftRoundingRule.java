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

@Entity
@Table(name = "shift_rounding_rule")
@Getter
@Setter
@NoArgsConstructor
public class ShiftRoundingRule extends BaseChildEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_shift_rounding_shift"))
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private RoundingKind kind;

    @Column(name = "unit_minutes", nullable = false)
    private int unitMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private RoundingMode mode;
}

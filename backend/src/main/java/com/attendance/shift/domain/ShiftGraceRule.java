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
@Table(name = "shift_grace_rule")
@Getter
@Setter
@NoArgsConstructor
public class ShiftGraceRule extends BaseChildEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_shift_grace_shift"))
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private GraceKind kind;

    @Column(name = "minutes", nullable = false)
    private int minutes;
}

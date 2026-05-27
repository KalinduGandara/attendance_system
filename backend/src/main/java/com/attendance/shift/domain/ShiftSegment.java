package com.attendance.shift.domain;

import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shift_segment")
@Getter
@Setter
@NoArgsConstructor
public class ShiftSegment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_shift_segment_shift"))
    private Shift shift;

    @Column(name = "segment_order", nullable = false)
    private int segmentOrder;

    @Column(name = "start_minute_of_day", nullable = false)
    private int startMinuteOfDay;

    @Column(name = "end_minute_of_day", nullable = false)
    private int endMinuteOfDay;

    @Column(name = "required_minutes")
    private Integer requiredMinutes;
}

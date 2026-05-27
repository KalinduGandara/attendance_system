package com.attendance.schedule.domain;

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
@Table(name = "schedule_template_day")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleTemplateDay extends BaseChildEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_std_template"))
    private ScheduleTemplate template;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shift_id", columnDefinition = "BINARY(16)")
    private UUID shiftId;
}

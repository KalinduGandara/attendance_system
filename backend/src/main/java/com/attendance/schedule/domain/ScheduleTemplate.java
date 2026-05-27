package com.attendance.schedule.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedule_template")
@Getter
@Setter
@NoArgsConstructor
@Auditable("ScheduleTemplate")
@EntityListeners(AuditEntityListener.class)
public class ScheduleTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 16)
    private CycleType cycleType;

    @Column(name = "cycle_length_days", nullable = false)
    private int cycleLengthDays;

    @Column(name = "description", length = 255)
    private String description;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("dayIndex ASC")
    private List<ScheduleTemplateDay> days = new ArrayList<>();
}

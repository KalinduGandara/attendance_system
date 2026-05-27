package com.attendance.timecode.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "time_code")
@Getter
@Setter
@NoArgsConstructor
@Auditable("TimeCode")
@EntityListeners(AuditEntityListener.class)
public class TimeCode extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private TimeCodeCategory category;

    @Column(name = "rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal rate;

    @Column(name = "color", nullable = false, columnDefinition = "CHAR(7)")
    private String color;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @Column(name = "counts_for_attendance", nullable = false)
    private boolean countsForAttendance;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}

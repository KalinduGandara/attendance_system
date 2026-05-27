package com.attendance.shift.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "shift")
@Getter
@Setter
@NoArgsConstructor
@Auditable("Shift")
@EntityListeners(AuditEntityListener.class)
public class Shift extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 16)
    private ShiftType shiftType;

    @Column(name = "color", nullable = false, columnDefinition = "CHAR(7)")
    private String color;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "attendance_time_code_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID attendanceTimeCodeId;

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("segmentOrder ASC")
    private List<ShiftSegment> segments = new ArrayList<>();

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ShiftRoundingRule> roundingRules = new ArrayList<>();

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ShiftGraceRule> graceRules = new ArrayList<>();

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<BreakRule> breakRules = new ArrayList<>();

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sequenceOrder ASC")
    private List<OvertimeRule> overtimeRules = new ArrayList<>();

    /**
     * For FLOATING shifts only: the candidate shifts that may be selected at
     * resolve time. Stored as a join table (floating_shift_candidate); we keep
     * the IDs rather than the full Shift refs to keep the engine's hot path
     * free of self-referencing joins.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "floating_shift_candidate",
            joinColumns = @JoinColumn(name = "floating_shift_id",
                    foreignKey = @jakarta.persistence.ForeignKey(name = "fk_floating_shift")))
    @Column(name = "candidate_shift_id", columnDefinition = "BINARY(16)", nullable = false)
    @JdbcTypeCode(SqlTypes.BINARY)
    private Set<UUID> candidateShiftIds = new HashSet<>();
}

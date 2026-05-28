package com.attendance.timecard.engine.snapshots;

import com.attendance.shift.domain.BreakKind;
import com.attendance.shift.domain.GraceKind;
import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.shift.domain.ShiftType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable representation of a shift + nested rules consumed by the engine. */
public record ShiftSnapshot(
        UUID id,
        String name,
        ShiftType shiftType,
        UUID attendanceTimeCodeId,
        List<SegmentSnapshot> segments,
        List<RoundingRuleSnapshot> roundingRules,
        List<GraceRuleSnapshot> graceRules,
        List<BreakRuleSnapshot> breakRules,
        List<OvertimeTierSnapshot> overtimeTiers,
        Set<UUID> candidateShiftIds) {

    public record SegmentSnapshot(int segmentOrder,
                                  int startMinuteOfDay,
                                  int endMinuteOfDay,
                                  Integer requiredMinutes) {
    }

    public record RoundingRuleSnapshot(RoundingKind kind, int unitMinutes, RoundingMode mode) {
    }

    public record GraceRuleSnapshot(GraceKind kind, int minutes) {
    }

    public record BreakRuleSnapshot(String name,
                                    BreakKind kind,
                                    int durationMinutes,
                                    Integer earliestStartMinute,
                                    Integer afterHoursWorked,
                                    boolean paid,
                                    UUID timeCodeId) {
    }

    public record OvertimeTierSnapshot(int sequenceOrder,
                                       int afterMinutesWorked,
                                       UUID timeCodeId,
                                       Integer maxMinutes) {
    }
}

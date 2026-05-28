package com.attendance.timecard.service;

import com.attendance.shift.domain.BreakRule;
import com.attendance.shift.domain.OvertimeRule;
import com.attendance.shift.domain.Shift;
import com.attendance.shift.domain.ShiftGraceRule;
import com.attendance.shift.domain.ShiftRoundingRule;
import com.attendance.shift.domain.ShiftSegment;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts JPA entities into the pure-engine snapshot records. The orchestrator
 * uses this so the engine never sees JPA proxies or lazy collections.
 */
final class EngineSnapshotMapper {

    private EngineSnapshotMapper() {
    }

    static ShiftSnapshot toShiftSnapshot(Shift s) {
        if (s == null) {
            return null;
        }
        return new ShiftSnapshot(
                s.getId(),
                s.getName(),
                s.getShiftType(),
                s.getAttendanceTimeCodeId(),
                s.getSegments().stream().map(EngineSnapshotMapper::toSegment).toList(),
                s.getRoundingRules().stream().map(EngineSnapshotMapper::toRoundingRule).toList(),
                s.getGraceRules().stream().map(EngineSnapshotMapper::toGraceRule).toList(),
                s.getBreakRules().stream().map(EngineSnapshotMapper::toBreakRule).toList(),
                s.getOvertimeRules().stream().map(EngineSnapshotMapper::toOvertimeTier).toList(),
                Set.copyOf(new HashSet<>(s.getCandidateShiftIds())));
    }

    static List<PunchSnapshot> toPunchSnapshots(List<PunchEvent> events) {
        return events.stream()
                .map(e -> new PunchSnapshot(e.getId(), e.getEventType(), e.getEventTimeUtc()))
                .toList();
    }

    private static ShiftSnapshot.SegmentSnapshot toSegment(ShiftSegment s) {
        return new ShiftSnapshot.SegmentSnapshot(
                s.getSegmentOrder(),
                s.getStartMinuteOfDay(),
                s.getEndMinuteOfDay(),
                s.getRequiredMinutes());
    }

    private static ShiftSnapshot.RoundingRuleSnapshot toRoundingRule(ShiftRoundingRule r) {
        return new ShiftSnapshot.RoundingRuleSnapshot(r.getKind(), r.getUnitMinutes(), r.getMode());
    }

    private static ShiftSnapshot.GraceRuleSnapshot toGraceRule(ShiftGraceRule g) {
        return new ShiftSnapshot.GraceRuleSnapshot(g.getKind(), g.getMinutes());
    }

    private static ShiftSnapshot.BreakRuleSnapshot toBreakRule(BreakRule b) {
        return new ShiftSnapshot.BreakRuleSnapshot(
                b.getName(),
                b.getKind(),
                b.getDurationMinutes(),
                b.getEarliestStartMinute(),
                b.getAfterHoursWorked(),
                b.isPaid(),
                b.getTimeCodeId());
    }

    private static ShiftSnapshot.OvertimeTierSnapshot toOvertimeTier(OvertimeRule o) {
        return new ShiftSnapshot.OvertimeTierSnapshot(
                o.getSequenceOrder(),
                o.getAfterMinutesWorked(),
                o.getTimeCodeId(),
                o.getMaxMinutes());
    }
}

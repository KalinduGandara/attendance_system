package com.attendance.timecard.engine;

import com.attendance.shift.domain.BreakKind;
import com.attendance.shift.domain.GraceKind;
import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.shift.domain.ShiftType;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tiny helpers for the engine unit tests so each scenario stays one-liner-readable. */
final class EngineTestFixtures {

    private EngineTestFixtures() {
    }

    static final ZoneId NY = ZoneId.of("America/New_York");
    static final UUID ATTEND = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID OT_A = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID OT_B = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID LUNCH = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID LEAVE = UUID.fromString("55555555-5555-5555-5555-555555555555");

    static Instant ts(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(NY).toInstant();
    }

    static PunchSnapshot punch(PunchEventType t, Instant at) {
        return new PunchSnapshot(UUID.randomUUID(), t, at);
    }

    static PunchSnapshot punch(UUID id, PunchEventType t, Instant at) {
        return new PunchSnapshot(id, t, at);
    }

    static ShiftSnapshot fixedNineToFive(UUID attendanceCode,
                                         List<ShiftSnapshot.OvertimeTierSnapshot> overtime,
                                         List<ShiftSnapshot.BreakRuleSnapshot> breaks,
                                         List<ShiftSnapshot.RoundingRuleSnapshot> rounding,
                                         List<ShiftSnapshot.GraceRuleSnapshot> grace) {
        return new ShiftSnapshot(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Day 9-5",
                ShiftType.FIXED,
                attendanceCode,
                List.of(new ShiftSnapshot.SegmentSnapshot(0, 9 * 60, 17 * 60, null)),
                rounding == null ? List.of() : rounding,
                grace == null ? List.of() : grace,
                breaks == null ? List.of() : breaks,
                overtime == null ? List.of() : overtime,
                Set.of());
    }

    static ShiftSnapshot.OvertimeTierSnapshot tier(int seq, int afterMinutes, UUID codeId, Integer maxMinutes) {
        return new ShiftSnapshot.OvertimeTierSnapshot(seq, afterMinutes, codeId, maxMinutes);
    }

    static ShiftSnapshot.BreakRuleSnapshot autoDeductLunch(int durationMinutes, int afterHoursWorked) {
        return new ShiftSnapshot.BreakRuleSnapshot(
                "Lunch", BreakKind.AUTO_DEDUCT, durationMinutes, null, afterHoursWorked, false, null);
    }

    static ShiftSnapshot.BreakRuleSnapshot punchTracked(boolean paid, UUID timeCodeId) {
        return new ShiftSnapshot.BreakRuleSnapshot(
                "Tea", BreakKind.PUNCH_TRACKED, 0, null, null, paid, timeCodeId);
    }

    static ShiftSnapshot.RoundingRuleSnapshot round(RoundingKind kind, int unit, RoundingMode mode) {
        return new ShiftSnapshot.RoundingRuleSnapshot(kind, unit, mode);
    }

    static ShiftSnapshot.GraceRuleSnapshot grace(GraceKind kind, int minutes) {
        return new ShiftSnapshot.GraceRuleSnapshot(kind, minutes);
    }

    static List<PunchSnapshot> punches(PunchSnapshot... ps) {
        List<PunchSnapshot> out = new ArrayList<>();
        for (PunchSnapshot p : ps) {
            out.add(p);
        }
        return out;
    }
}

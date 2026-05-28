package com.attendance.timecard.engine;

import com.attendance.shift.domain.GraceKind;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Nudges actual punches toward the scheduled boundary when they fall within
 * the configured grace tolerance. After grace is applied, any remaining
 * delta becomes "late minutes" / "early out minutes" that the exception
 * detector may emit.
 */
public final class GraceApplier {

    private GraceApplier() {
    }

    public static GraceResult apply(List<Interval> intervals,
                                    ScheduleAnchor.Window scheduled,
                                    List<ShiftSnapshot.GraceRuleSnapshot> rules) {
        if (intervals.isEmpty() || scheduled == null) {
            return new GraceResult(intervals, 0, 0);
        }

        int lateGrace = ruleMinutes(rules, GraceKind.LATE_IN);
        int earlyOutGrace = ruleMinutes(rules, GraceKind.EARLY_OUT);

        List<Interval> adjusted = new ArrayList<>(intervals);
        Interval first = adjusted.get(0);
        Interval last = adjusted.get(adjusted.size() - 1);

        Instant actualStart = first.start();
        Instant actualEnd = last.end();

        int lateMinutes = 0;
        if (actualStart.isAfter(scheduled.start())) {
            long deltaMinutes = Duration.between(scheduled.start(), actualStart).toMinutes();
            if (deltaMinutes <= lateGrace) {
                actualStart = scheduled.start();
            } else {
                lateMinutes = (int) deltaMinutes;
            }
        }

        int earlyOutMinutes = 0;
        if (actualEnd.isBefore(scheduled.end())) {
            long deltaMinutes = Duration.between(actualEnd, scheduled.end()).toMinutes();
            if (deltaMinutes <= earlyOutGrace) {
                actualEnd = scheduled.end();
            } else {
                earlyOutMinutes = (int) deltaMinutes;
            }
        }

        adjusted.set(0, first.withStart(actualStart));
        if (adjusted.size() == 1) {
            adjusted.set(0, adjusted.get(0).withEnd(actualEnd));
        } else {
            adjusted.set(adjusted.size() - 1, last.withEnd(actualEnd));
        }
        return new GraceResult(adjusted, lateMinutes, earlyOutMinutes);
    }

    private static int ruleMinutes(List<ShiftSnapshot.GraceRuleSnapshot> rules, GraceKind kind) {
        return rules.stream()
                .filter(r -> r.kind() == kind)
                .mapToInt(ShiftSnapshot.GraceRuleSnapshot::minutes)
                .max()
                .orElse(0);
    }

    public record GraceResult(List<Interval> intervals, int lateMinutes, int earlyOutMinutes) {
    }
}

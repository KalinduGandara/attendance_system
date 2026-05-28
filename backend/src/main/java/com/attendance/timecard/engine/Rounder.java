package com.attendance.timecard.engine;

import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the shift's rounding rules to a list of work intervals. The kinds
 * (SHIFT / PUNCH_IN / PUNCH_OUT) are applied in that order; the last rule of
 * each kind wins.
 *
 * <p>SHIFT rounds the overall envelope (first start + last end together) which
 * mirrors the canonical T&amp;A semantics: one rule applies to the whole shift.
 */
public final class Rounder {

    private Rounder() {
    }

    public static List<Interval> applyRoundingRules(List<Interval> intervals,
                                                    List<ShiftSnapshot.RoundingRuleSnapshot> rules) {
        if (intervals.isEmpty() || rules.isEmpty()) {
            return intervals;
        }
        List<Interval> result = new ArrayList<>(intervals);

        for (ShiftSnapshot.RoundingRuleSnapshot rule : rules) {
            result = switch (rule.kind()) {
                case PUNCH_IN -> applyPerInterval(result, rule, true, false);
                case PUNCH_OUT -> applyPerInterval(result, rule, false, true);
                case SHIFT -> applyShift(result, rule);
            };
        }
        return result;
    }

    private static List<Interval> applyPerInterval(List<Interval> in,
                                                   ShiftSnapshot.RoundingRuleSnapshot rule,
                                                   boolean roundStart,
                                                   boolean roundEnd) {
        List<Interval> out = new ArrayList<>(in.size());
        for (Interval i : in) {
            Instant s = roundStart ? round(i.start(), rule.unitMinutes(), rule.mode()) : i.start();
            Instant e = roundEnd ? round(i.end(), rule.unitMinutes(), rule.mode()) : i.end();
            if (e.isAfter(s)) {
                out.add(new Interval(s, e));
            } else {
                out.add(i);
            }
        }
        return out;
    }

    private static List<Interval> applyShift(List<Interval> in, ShiftSnapshot.RoundingRuleSnapshot rule) {
        Instant first = in.get(0).start();
        Instant last = in.get(in.size() - 1).end();
        Instant roundedFirst = round(first, rule.unitMinutes(), rule.mode());
        Instant roundedLast = round(last, rule.unitMinutes(), rule.mode());
        if (!roundedLast.isAfter(roundedFirst)) {
            return in;
        }
        List<Interval> out = new ArrayList<>(in);
        if (out.size() == 1) {
            out.set(0, new Interval(roundedFirst, roundedLast));
        } else {
            out.set(0, in.get(0).withStart(roundedFirst));
            out.set(out.size() - 1, in.get(in.size() - 1).withEnd(roundedLast));
        }
        return out;
    }

    /** Rounds an instant to the given minute boundary. Visible for unit tests. */
    public static Instant round(Instant t, int unitMinutes, RoundingMode mode) {
        if (unitMinutes <= 0) {
            return t;
        }
        long totalMinutes = Math.floorDiv(t.getEpochSecond(), 60L);
        long remainder = Math.floorMod(totalMinutes, (long) unitMinutes);
        long rounded = switch (mode) {
            case UP -> remainder == 0 ? totalMinutes : totalMinutes - remainder + unitMinutes;
            case DOWN -> totalMinutes - remainder;
            case NEAREST -> remainder * 2 < unitMinutes
                    ? totalMinutes - remainder
                    : totalMinutes - remainder + unitMinutes;
        };
        return Instant.ofEpochSecond(rounded * 60L);
    }

    public static List<ShiftSnapshot.RoundingRuleSnapshot> ofKind(
            List<ShiftSnapshot.RoundingRuleSnapshot> rules, RoundingKind kind) {
        return rules.stream().filter(r -> r.kind() == kind).toList();
    }
}

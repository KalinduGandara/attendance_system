package com.attendance.timecard.engine;

import com.attendance.shift.domain.BreakKind;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles work and break intervals against the shift's break rules.
 *
 * <ul>
 *   <li>PUNCH_TRACKED breaks: every break interval is subtracted from worked
 *       minutes. If the rule is paid, those minutes accumulate as a paid
 *       break attribution (so reports show paid-break time separately).</li>
 *   <li>AUTO_DEDUCT breaks: a single fixed deduction is applied once the
 *       configured {@code afterHoursWorked} threshold is met. Unpaid
 *       deductions reduce worked minutes; paid auto-deductions add to a
 *       paid-break attribution without touching worked minutes.</li>
 * </ul>
 */
public final class BreakDeducer {

    private BreakDeducer() {
    }

    public static DeductionResult deduce(List<Interval> workIntervals,
                                         List<Interval> breakIntervals,
                                         List<ShiftSnapshot.BreakRuleSnapshot> rules) {
        int rawWorkedMinutes = workIntervals.stream().mapToInt(Interval::durationMinutes).sum();
        int trackedBreakMinutes = breakIntervals.stream().mapToInt(Interval::durationMinutes).sum();

        int unpaidBreakDeduction = 0;
        int paidBreakMinutes = 0;
        List<PaidBreakAttribution> attributions = new ArrayList<>();

        // Tracked-break rules: split tracked break minutes into paid vs unpaid.
        // We assume one PUNCH_TRACKED rule defines the policy; if multiple exist,
        // we use the first that is paid (paid wins so tracked breaks default to
        // counting as worked-equivalent only when explicitly paid).
        ShiftSnapshot.BreakRuleSnapshot trackedRule = rules.stream()
                .filter(r -> r.kind() == BreakKind.PUNCH_TRACKED)
                .findFirst()
                .orElse(null);
        if (trackedRule != null && trackedBreakMinutes > 0) {
            if (trackedRule.paid()) {
                paidBreakMinutes += trackedBreakMinutes;
                if (trackedRule.timeCodeId() != null) {
                    attributions.add(new PaidBreakAttribution(trackedRule.timeCodeId(), trackedBreakMinutes));
                }
            } else {
                unpaidBreakDeduction += trackedBreakMinutes;
            }
        }

        // Auto-deduct rules: one-shot, applied once after_hours_worked is met.
        for (ShiftSnapshot.BreakRuleSnapshot rule : rules) {
            if (rule.kind() != BreakKind.AUTO_DEDUCT) {
                continue;
            }
            int after = rule.afterHoursWorked() == null ? 0 : rule.afterHoursWorked();
            if (rawWorkedMinutes - unpaidBreakDeduction < after) {
                continue;
            }
            if (rule.paid()) {
                paidBreakMinutes += rule.durationMinutes();
                if (rule.timeCodeId() != null) {
                    attributions.add(new PaidBreakAttribution(rule.timeCodeId(), rule.durationMinutes()));
                }
            } else {
                unpaidBreakDeduction += rule.durationMinutes();
            }
        }

        int workedMinutes = Math.max(0, rawWorkedMinutes - unpaidBreakDeduction);
        int totalBreakMinutes = unpaidBreakDeduction + paidBreakMinutes;
        return new DeductionResult(workedMinutes, totalBreakMinutes, attributions);
    }

    public record DeductionResult(int workedMinutes,
                                  int breakMinutes,
                                  List<PaidBreakAttribution> paidBreakAttributions) {
    }

    public record PaidBreakAttribution(UUID timeCodeId, int minutes) {
    }
}

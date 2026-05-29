package com.attendance.timecard.engine;

import com.attendance.shift.domain.ShiftType;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.engine.EngineOutput.BreakdownLine;
import com.attendance.timecard.engine.EngineOutput.EmittedException;
import com.attendance.timecard.engine.snapshots.LeaveSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The pure time-card computation engine. Given {@link EngineInputs} it
 * returns an {@link EngineOutput} that the orchestrator persists.
 *
 * <p>The algorithm follows architecture.md §6 exactly. Each numbered step
 * delegates to a single named subroutine; this method is intentionally a
 * straight-line read of the spec.
 */
public final class TimeCardCalculator {

    private TimeCardCalculator() {
    }

    public static EngineOutput compute(EngineInputs in) {
        // 1. Schedule already resolved by caller.
        // 2. Holiday / leave gate.
        if (in.holiday()) {
            return holidayOrLeaveOutput(in, DailyTimeCardStatus.HOLIDAY, attendanceTimeCodeId(in), 0);
        }
        if (in.leave() != null && !in.leave().halfDay()) {
            return holidayOrLeaveOutput(in, DailyTimeCardStatus.LEAVE,
                    in.leave().leaveTypeTimeCodeId(),
                    fullDayMinutes(in));
        }

        ShiftSnapshot shift = in.shift();

        // No scheduled day + no punches → OFF or UNSCHEDULED.
        if (in.scheduledState() == EngineInputs.ScheduledState.OFF && in.punches().isEmpty()) {
            return EngineOutputs.empty(DailyTimeCardStatus.OFF, null);
        }
        if (in.scheduledState() == EngineInputs.ScheduledState.UNSCHEDULED && in.punches().isEmpty()) {
            return EngineOutputs.empty(DailyTimeCardStatus.OFF, null);
        }

        // 3. Scheduled but no punches → ABSENT (unless a half-day leave covers part of the day).
        if (in.scheduledState() == EngineInputs.ScheduledState.SCHEDULED && in.punches().isEmpty()) {
            if (in.leave() != null && in.leave().halfDay()) {
                return halfDayLeaveOnlyOutput(in);
            }
            return absentOutput(in);
        }

        // Floating shift: pick a concrete candidate before doing anything else.
        if (shift != null && shift.shiftType() == ShiftType.FLOATING) {
            // The caller passes the concrete chosen shift via shiftOpt(); if not,
            // we treat the day as UNSCHEDULED with punches → orphan path below.
        }

        UUID resolvedShiftId = shift == null ? null : shift.id();

        // 4. Pair punches.
        PunchPairer.PairingResult pairing = PunchPairer.pair(in.punches());

        // Day-off / unscheduled with punches → ORPHAN_PUNCH, no breakdown.
        if (shift == null || in.scheduledState() == EngineInputs.ScheduledState.OFF) {
            int rawWorked = pairing.work().stream().mapToInt(Interval::durationMinutes).sum();
            List<EmittedException> orphan = ExceptionDetector.detect(new ExceptionDetector.DetectionInputs(
                    pairing.missingIn(), pairing.missingOut(),
                    0, 0, false, true, 0));
            return new EngineOutput(
                    DailyTimeCardStatus.OFF,
                    resolvedShiftId,
                    null, null,
                    pairing.work().isEmpty() ? null : pairing.work().get(0).start(),
                    pairing.work().isEmpty() ? null : pairing.work().get(pairing.work().size() - 1).end(),
                    rawWorked, 0, 0, 0, 0,
                    List.of(), orphan, null);
        }

        // 5. Rounding.
        List<Interval> rounded = Rounder.applyRoundingRules(pairing.work(), shift.roundingRules());

        // Schedule window.
        ScheduleAnchor.Window window = ScheduleAnchor.resolve(in.workDate(), in.zone(), shift).orElse(null);

        // 6. Grace — only meaningful for FIXED shifts; FLEX/FLOATING have no
        //    enforceable start/end so we leave the intervals untouched.
        GraceApplier.GraceResult graced = shift.shiftType() == ShiftType.FIXED
                ? GraceApplier.apply(rounded, window, shift.graceRules())
                : new GraceApplier.GraceResult(rounded, 0, 0);
        List<Interval> adjustedWork = graced.intervals();

        // 7. Break deduction.
        BreakDeducer.DeductionResult deduction = BreakDeducer.deduce(
                adjustedWork, pairing.breaks(), shift.breakRules());

        int workedMinutes = deduction.workedMinutes();
        int breakMinutes = deduction.breakMinutes();

        // 8. OT tier slicing.
        UUID attendanceCode = shift.attendanceTimeCodeId();
        List<OvertimeTierSlicer.Slice> slices = OvertimeTierSlicer.slice(
                workedMinutes, attendanceCode, shift.overtimeTiers());

        // Add paid-break attributions as breakdown lines that follow the main slices.
        int seq = slices.size();
        List<EngineOutput.BreakdownLine> breakdowns = new ArrayList<>();
        for (OvertimeTierSlicer.Slice s : slices) {
            int rated = RatedMinutesCalculator.rated(s.minutes(), s.timeCodeId(), in.timeCodeRates());
            breakdowns.add(new BreakdownLine(s.timeCodeId(), s.minutes(), rated, s.sequenceOrder()));
        }
        // 9. Rated minutes calc already happens above. Paid breaks:
        Map<UUID, Integer> paidBreakRollup = new LinkedHashMap<>();
        for (BreakDeducer.PaidBreakAttribution attr : deduction.paidBreakAttributions()) {
            paidBreakRollup.merge(attr.timeCodeId(), attr.minutes(), Integer::sum);
        }
        for (Map.Entry<UUID, Integer> e : paidBreakRollup.entrySet()) {
            int rated = RatedMinutesCalculator.rated(e.getValue(), e.getKey(), in.timeCodeRates());
            breakdowns.add(new BreakdownLine(e.getKey(), e.getValue(), rated, seq++));
        }
        // Half-day leave fills half the scheduled minutes with the leave code.
        if (in.leave() != null && in.leave().halfDay()) {
            int halfDay = window == null ? 240 : window.durationMinutes() / 2;
            int rated = RatedMinutesCalculator.rated(halfDay, in.leave().leaveTypeTimeCodeId(), in.timeCodeRates());
            breakdowns.add(new BreakdownLine(in.leave().leaveTypeTimeCodeId(), halfDay, rated, seq++));
        }

        int overtimeMinutes = slices.stream().filter(OvertimeTierSlicer.Slice::overtime)
                .mapToInt(OvertimeTierSlicer.Slice::minutes).sum();

        // 10. Exceptions.
        int unauthorizedOt = ExceptionDetector.unauthorizedOt(workedMinutes, shift.overtimeTiers());
        List<EmittedException> exceptions = ExceptionDetector.detect(new ExceptionDetector.DetectionInputs(
                pairing.missingIn(),
                pairing.missingOut(),
                graced.lateMinutes(),
                graced.earlyOutMinutes(),
                false,
                false,
                unauthorizedOt));

        // 11. Status.
        DailyTimeCardStatus status = decideStatus(shift, workedMinutes, window, pairing.missingIn(),
                pairing.missingOut(), graced.lateMinutes(), graced.earlyOutMinutes(), in.leave());

        java.time.Instant actualStart = adjustedWork.isEmpty() ? null : adjustedWork.get(0).start();
        java.time.Instant actualEnd = adjustedWork.isEmpty() ? null : adjustedWork.get(adjustedWork.size() - 1).end();

        return new EngineOutput(
                status,
                resolvedShiftId,
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                actualStart,
                actualEnd,
                workedMinutes,
                breakMinutes,
                overtimeMinutes,
                graced.lateMinutes(),
                graced.earlyOutMinutes(),
                breakdowns,
                exceptions,
                null);
    }

    private static EngineOutput holidayOrLeaveOutput(EngineInputs in,
                                                     DailyTimeCardStatus status,
                                                     UUID timeCodeId,
                                                     int minutes) {
        List<EngineOutput.BreakdownLine> breakdowns = new ArrayList<>();
        if (timeCodeId != null && minutes > 0) {
            int rated = RatedMinutesCalculator.rated(minutes, timeCodeId, in.timeCodeRates());
            breakdowns.add(new BreakdownLine(timeCodeId, minutes, rated, 0));
        }
        return new EngineOutput(
                status,
                in.shift() == null ? null : in.shift().id(),
                null, null,
                null, null,
                0, 0, 0, 0, 0,
                breakdowns,
                List.of(),
                null);
    }

    private static EngineOutput halfDayLeaveOnlyOutput(EngineInputs in) {
        ScheduleAnchor.Window window = in.shift() == null
                ? null
                : ScheduleAnchor.resolve(in.workDate(), in.zone(), in.shift()).orElse(null);
        int halfDay = window == null ? 240 : window.durationMinutes() / 2;
        List<EngineOutput.BreakdownLine> breakdowns = new ArrayList<>();
        if (in.leave().leaveTypeTimeCodeId() != null && halfDay > 0) {
            int rated = RatedMinutesCalculator.rated(halfDay, in.leave().leaveTypeTimeCodeId(),
                    in.timeCodeRates());
            breakdowns.add(new BreakdownLine(in.leave().leaveTypeTimeCodeId(), halfDay, rated, 0));
        }
        return new EngineOutput(
                DailyTimeCardStatus.LEAVE,
                in.shift() == null ? null : in.shift().id(),
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                null, null,
                0, 0, 0, 0, 0,
                breakdowns,
                List.of(),
                null);
    }

    private static EngineOutput absentOutput(EngineInputs in) {
        ScheduleAnchor.Window window = ScheduleAnchor.resolve(in.workDate(), in.zone(), in.shift()).orElse(null);
        List<EmittedException> exceptions = List.of(
                new EmittedException(com.attendance.exception.domain.ExceptionType.ABSENT_NO_LEAVE,
                        com.attendance.exception.domain.ExceptionSeverity.CRITICAL));
        return new EngineOutput(
                DailyTimeCardStatus.ABSENT,
                in.shift() == null ? null : in.shift().id(),
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                null, null,
                0, 0, 0, 0, 0,
                List.of(),
                exceptions,
                null);
    }

    private static int fullDayMinutes(EngineInputs in) {
        if (in.shift() == null) {
            return 0;
        }
        return ScheduleAnchor.resolve(in.workDate(), in.zone(), in.shift())
                .map(ScheduleAnchor.Window::durationMinutes)
                .orElse(0);
    }

    private static UUID attendanceTimeCodeId(EngineInputs in) {
        return in.shift() == null ? null : in.shift().attendanceTimeCodeId();
    }

    private static DailyTimeCardStatus decideStatus(ShiftSnapshot shift,
                                                    int workedMinutes,
                                                    ScheduleAnchor.Window window,
                                                    int missingIn,
                                                    int missingOut,
                                                    int lateMinutes,
                                                    int earlyOutMinutes,
                                                    LeaveSnapshot leave) {
        if (leave != null && leave.halfDay()) {
            return workedMinutes > 0 ? DailyTimeCardStatus.PARTIAL : DailyTimeCardStatus.LEAVE;
        }
        if (workedMinutes == 0) {
            return DailyTimeCardStatus.ABSENT;
        }
        if (missingIn > 0 || missingOut > 0) {
            return DailyTimeCardStatus.PARTIAL;
        }
        if (shift.shiftType() == ShiftType.FLEXIBLE) {
            int required = shift.segments().stream()
                    .mapToInt(s -> s.requiredMinutes() == null ? 0 : s.requiredMinutes())
                    .sum();
            return workedMinutes >= required ? DailyTimeCardStatus.PRESENT : DailyTimeCardStatus.PARTIAL;
        }
        // FIXED / FLOATING: any unaccounted late or early-out → PARTIAL.
        if (lateMinutes > 0 || earlyOutMinutes > 0) {
            return DailyTimeCardStatus.PARTIAL;
        }
        if (window != null) {
            int scheduledMinutes = window.durationMinutes();
            if (workedMinutes < scheduledMinutes - 60) {
                return DailyTimeCardStatus.PARTIAL;
            }
        }
        return DailyTimeCardStatus.PRESENT;
    }
}

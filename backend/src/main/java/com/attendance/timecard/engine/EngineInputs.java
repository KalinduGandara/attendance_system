package com.attendance.timecard.engine;

import com.attendance.timecard.engine.snapshots.LeaveSnapshot;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bundle of every input the time-card engine needs. Constructed by the
 * impure orchestrator; the engine itself never reads anything outside this
 * record (no DB, no Clock, no Spring).
 *
 * @param shift            null when {@link ScheduledState#OFF} or {@link ScheduledState#UNSCHEDULED}.
 * @param scheduledState   why we're computing — see {@link ScheduledState}.
 * @param punches          stable-sorted ascending by event_time_utc, then id.
 * @param holiday          set when the day is a holiday for this employee.
 * @param leave            set when an approved leave applies.
 * @param zone             employee timezone used to convert dates ↔ Instants.
 * @param timeCodeRates    rate per time-code id used by RatedMinutesCalculator.
 */
public record EngineInputs(
        UUID employeeId,
        LocalDate workDate,
        ZoneId zone,
        ScheduledState scheduledState,
        ShiftSnapshot shift,
        List<PunchSnapshot> punches,
        boolean holiday,
        LeaveSnapshot leave,
        Map<UUID, BigDecimal> timeCodeRates) {

    public enum ScheduledState {
        /** A shift is scheduled for the day. */
        SCHEDULED,
        /** Schedule explicitly resolves to a day off (null shift or off template day). */
        OFF,
        /** No schedule covers this day at all. */
        UNSCHEDULED
    }

    public Optional<ShiftSnapshot> shiftOpt() {
        return Optional.ofNullable(shift);
    }
}

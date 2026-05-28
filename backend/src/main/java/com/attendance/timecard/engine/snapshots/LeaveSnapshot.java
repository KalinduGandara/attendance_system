package com.attendance.timecard.engine.snapshots;

import java.util.UUID;

/**
 * An approved leave applicable to the work date. {@code halfDay} signals
 * that only half a day is on leave; the engine fills the other half from
 * punches if any.
 */
public record LeaveSnapshot(
        UUID leaveTypeTimeCodeId,
        boolean halfDay) {

    public static LeaveSnapshot fullDay(UUID timeCodeId) {
        return new LeaveSnapshot(timeCodeId, false);
    }
}

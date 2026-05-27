package com.attendance.schedule.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Result of resolving the schedule for a given (employee, date).
 *
 * <ul>
 *   <li>{@code source} — which rule won (TEMPORARY / EMPLOYEE / GROUP / NONE).</li>
 *   <li>{@code shiftId} — the shift to apply for the day; {@code null} means
 *       "no shift" (explicit day-off via a temporary entry, or a template day
 *       with no shift assigned). Combined with {@code source != NONE}, a null
 *       shiftId is a deliberate day-off rather than a missing schedule.</li>
 *   <li>{@code templateId} — null for TEMPORARY and NONE sources.</li>
 *   <li>{@code dayIndex} — the index resolved within the cycle (for diagnostics
 *       and the time-card UI). {@code -1} when not applicable.</li>
 *   <li>{@code assignmentId} — the winning assignment id, when applicable.</li>
 *   <li>{@code temporaryScheduleId} — the winning temporary schedule id, when
 *       applicable.</li>
 * </ul>
 */
public record ResolvedSchedule(
        UUID employeeId,
        LocalDate date,
        Source source,
        UUID shiftId,
        UUID templateId,
        int dayIndex,
        UUID assignmentId,
        UUID temporaryScheduleId) {

    public enum Source {
        TEMPORARY,
        EMPLOYEE_ASSIGNMENT,
        GROUP_ASSIGNMENT,
        NONE
    }

    public boolean hasShift() {
        return shiftId != null;
    }

    public static ResolvedSchedule none(UUID employeeId, LocalDate date) {
        return new ResolvedSchedule(employeeId, date, Source.NONE, null, null, -1, null, null);
    }
}

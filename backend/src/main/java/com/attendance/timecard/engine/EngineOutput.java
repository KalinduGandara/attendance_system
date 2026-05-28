package com.attendance.timecard.engine;

import com.attendance.exception.domain.ExceptionSeverity;
import com.attendance.exception.domain.ExceptionType;
import com.attendance.timecard.domain.DailyTimeCardStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Output of a pure compute() invocation. The orchestrator persists this into
 * {@code daily_time_card}, {@code time_card_breakdown}, and {@code exception_event}.
 */
public record EngineOutput(
        DailyTimeCardStatus status,
        UUID resolvedShiftId,
        Instant scheduledStartUtc,
        Instant scheduledEndUtc,
        Instant actualStartUtc,
        Instant actualEndUtc,
        int workedMinutes,
        int breakMinutes,
        int overtimeMinutes,
        int lateMinutes,
        int earlyOutMinutes,
        List<BreakdownLine> breakdowns,
        List<EmittedException> exceptions,
        String notes) {

    /** Per-time-code minutes attribution within the day. */
    public record BreakdownLine(UUID timeCodeId, int minutes, int ratedMinutes, int sequenceOrder) {
    }

    public record EmittedException(ExceptionType type, ExceptionSeverity severity, String detailsJson) {
        public EmittedException(ExceptionType type, ExceptionSeverity severity) {
            this(type, severity, null);
        }
    }
}

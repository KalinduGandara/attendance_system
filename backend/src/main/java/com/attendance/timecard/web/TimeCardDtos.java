package com.attendance.timecard.web;

import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.domain.TimeCardEditChangeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TimeCardDtos {

    private TimeCardDtos() {
    }

    public record EmployeeRef(UUID id, String code, String name) {
    }

    public record ShiftRef(UUID id, String name, String color) {
    }

    public record BreakdownDto(String timeCode,
                               UUID timeCodeId,
                               int minutes,
                               int ratedMinutes,
                               int sequenceOrder) {
    }

    public record ExceptionRef(UUID id, String type, String severity, String status) {
    }

    public record TimeCardResponse(
            UUID id,
            EmployeeRef employee,
            LocalDate workDate,
            DailyTimeCardStatus status,
            ShiftRef resolvedShift,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant actualStart,
            Instant actualEnd,
            int workedMinutes,
            int breakMinutes,
            int overtimeMinutes,
            int lateMinutes,
            int earlyOutMinutes,
            List<BreakdownDto> breakdown,
            List<ExceptionRef> exceptions,
            Instant computedAt,
            Long version) {
    }

    public record PunchEventResponse(
            UUID id,
            UUID employeeId,
            UUID deviceId,
            UUID ingestionSourceId,
            String externalEventId,
            PunchEventType eventType,
            Instant eventTimeUtc,
            PunchEventStatus status,
            Instant processedAt) {
    }

    public record TimeCardEditDto(
            UUID id,
            UUID punchEventId,
            TimeCardEditChangeType changeType,
            String beforeJson,
            String afterJson,
            String reason,
            UUID editedByUserId,
            Instant editedAt) {
    }

    /**
     * Detailed view of a time card returned by {@code GET /timecards/{id}} and
     * by {@code POST /timecards/{id}/edits}. Adds raw punches and the manual-
     * edit log to the summary {@link TimeCardResponse} fields.
     */
    public record TimeCardDetailResponse(
            UUID id,
            EmployeeRef employee,
            LocalDate workDate,
            DailyTimeCardStatus status,
            ShiftRef resolvedShift,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant actualStart,
            Instant actualEnd,
            int workedMinutes,
            int breakMinutes,
            int overtimeMinutes,
            int lateMinutes,
            int earlyOutMinutes,
            String notes,
            List<BreakdownDto> breakdown,
            List<ExceptionRef> exceptions,
            List<PunchEventResponse> punches,
            List<TimeCardEditDto> edits,
            Instant computedAt,
            Long version) {
    }

    public record EditRequest(
            @NotNull TimeCardEditChangeType changeType,
            UUID punchEventId,
            PunchEventType eventType,
            Instant newEventTime,
            UUID ingestionSourceId,
            DailyTimeCardStatus newStatus,
            @Size(max = 500) String newNotes,
            @NotNull @Size(min = 1, max = 500) String reason) {
    }

    public record RecomputeRequest(
            List<UUID> employeeIds,
            LocalDate from,
            LocalDate to) {
    }

    public record RecomputeResponse(int recomputedDays) {
    }
}

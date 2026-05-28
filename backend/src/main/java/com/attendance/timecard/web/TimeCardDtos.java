package com.attendance.timecard.web;

import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;

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

    public record RecomputeRequest(
            List<UUID> employeeIds,
            LocalDate from,
            LocalDate to) {
    }

    public record RecomputeResponse(int recomputedDays) {
    }
}

package com.attendance.schedule.web;

import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.domain.CycleType;
import com.attendance.schedule.service.ResolvedSchedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    // ------- Template -------

    public record TemplateDayRequest(
            @Min(0) @Max(365) int dayIndex,
            UUID shiftId) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 128) String name,
            @NotNull CycleType cycleType,
            @Min(1) @Max(366) int cycleLengthDays,
            @Size(max = 255) String description,
            @Valid List<TemplateDayRequest> days,
            Long expectedVersion) {
    }

    public record TemplateDayResponse(
            UUID id,
            int dayIndex,
            UUID shiftId) {
    }

    public record TemplateResponse(
            UUID id,
            String name,
            CycleType cycleType,
            int cycleLengthDays,
            String description,
            List<TemplateDayResponse> days,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    // ------- Assignment -------

    public record AssignmentRequest(
            @NotNull AssignmentTargetType targetType,
            @NotNull UUID targetId,
            @NotNull UUID templateId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @PositiveOrZero Integer priority,
            Long expectedVersion) {
    }

    public record AssignmentResponse(
            UUID id,
            AssignmentTargetType targetType,
            UUID targetId,
            UUID templateId,
            LocalDate startDate,
            LocalDate endDate,
            int priority,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    // ------- Temporary schedule -------

    public record TemporaryScheduleRequest(
            @NotNull UUID employeeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            UUID shiftId,
            @Size(max = 255) String reason,
            Long expectedVersion) {
    }

    public record TemporaryScheduleResponse(
            UUID id,
            UUID employeeId,
            LocalDate startDate,
            LocalDate endDate,
            UUID shiftId,
            String reason,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    // ------- Resolved schedule -------

    public record ResolvedScheduleResponse(
            UUID employeeId,
            LocalDate date,
            ResolvedSchedule.Source source,
            UUID shiftId,
            UUID templateId,
            int dayIndex,
            UUID assignmentId,
            UUID temporaryScheduleId) {

        public static ResolvedScheduleResponse from(ResolvedSchedule r) {
            return new ResolvedScheduleResponse(
                    r.employeeId(), r.date(), r.source(), r.shiftId(),
                    r.templateId(), r.dayIndex(), r.assignmentId(),
                    r.temporaryScheduleId());
        }
    }
}

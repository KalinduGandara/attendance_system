package com.attendance.shift.web;

import com.attendance.shift.domain.BreakKind;
import com.attendance.shift.domain.GraceKind;
import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.shift.domain.ShiftType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ShiftDtos {

    private ShiftDtos() {
    }

    public record SegmentRequest(
            @Min(0) int segmentOrder,
            @Min(0) @Max(2880) int startMinuteOfDay,
            @Min(1) @Max(2880) int endMinuteOfDay,
            @PositiveOrZero Integer requiredMinutes) {
    }

    public record RoundingRuleRequest(
            @NotNull RoundingKind kind,
            @Min(1) @Max(120) int unitMinutes,
            @NotNull RoundingMode mode) {
    }

    public record GraceRuleRequest(
            @NotNull GraceKind kind,
            @Min(0) @Max(240) int minutes) {
    }

    public record BreakRuleRequest(
            @NotBlank @Size(max = 64) String name,
            @NotNull BreakKind kind,
            @Min(0) @Max(480) int durationMinutes,
            Integer earliestStartMinute,
            Integer afterHoursWorked,
            @NotNull Boolean paid,
            UUID timeCodeId) {
    }

    public record OvertimeRuleRequest(
            @Min(0) int sequenceOrder,
            @Min(0) int afterMinutesWorked,
            @NotNull UUID timeCodeId,
            @PositiveOrZero Integer maxMinutes) {
    }

    public record ShiftRequest(
            @NotBlank @Size(max = 128) String name,
            @NotNull ShiftType shiftType,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$",
                    message = "must be a hex color like #3b82f6") String color,
            @Size(max = 64) String timezone,
            @Size(max = 255) String description,
            Boolean active,
            @NotNull UUID attendanceTimeCodeId,
            @Valid List<SegmentRequest> segments,
            @Valid List<RoundingRuleRequest> roundingRules,
            @Valid List<GraceRuleRequest> graceRules,
            @Valid List<BreakRuleRequest> breakRules,
            @Valid List<OvertimeRuleRequest> overtimeRules,
            Set<UUID> candidateShiftIds,
            Long expectedVersion) {
    }

    public record SegmentResponse(
            UUID id,
            int segmentOrder,
            int startMinuteOfDay,
            int endMinuteOfDay,
            Integer requiredMinutes) {
    }

    public record RoundingRuleResponse(
            UUID id,
            RoundingKind kind,
            int unitMinutes,
            RoundingMode mode) {
    }

    public record GraceRuleResponse(
            UUID id,
            GraceKind kind,
            int minutes) {
    }

    public record BreakRuleResponse(
            UUID id,
            String name,
            BreakKind kind,
            int durationMinutes,
            Integer earliestStartMinute,
            Integer afterHoursWorked,
            boolean paid,
            UUID timeCodeId) {
    }

    public record OvertimeRuleResponse(
            UUID id,
            int sequenceOrder,
            int afterMinutesWorked,
            UUID timeCodeId,
            Integer maxMinutes) {
    }

    public record ShiftResponse(
            UUID id,
            String name,
            ShiftType shiftType,
            String color,
            String timezone,
            String description,
            boolean active,
            UUID attendanceTimeCodeId,
            List<SegmentResponse> segments,
            List<RoundingRuleResponse> roundingRules,
            List<GraceRuleResponse> graceRules,
            List<BreakRuleResponse> breakRules,
            List<OvertimeRuleResponse> overtimeRules,
            Set<UUID> candidateShiftIds,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }
}

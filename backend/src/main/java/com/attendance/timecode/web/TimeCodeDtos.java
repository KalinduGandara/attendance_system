package com.attendance.timecode.web;

import com.attendance.timecode.domain.TimeCodeCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TimeCodeDtos {

    private TimeCodeDtos() {
    }

    public record TimeCodeRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                    message = "must be alphanumeric with . _ -") String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull TimeCodeCategory category,
            @NotNull @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal rate,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$",
                    message = "must be a hex color like #3b82f6") String color,
            @NotNull Boolean paid,
            @NotNull Boolean countsForAttendance,
            @Size(max = 255) String description,
            Boolean active) {
    }

    public record TimeCodeResponse(
            UUID id,
            String code,
            String name,
            TimeCodeCategory category,
            BigDecimal rate,
            String color,
            boolean paid,
            boolean countsForAttendance,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }
}

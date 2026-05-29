package com.attendance.exception.web;

import com.attendance.exception.domain.ExceptionSeverity;
import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.domain.ExceptionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ExceptionDtos {

    private ExceptionDtos() {
    }

    public record ExceptionEventResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID dailyTimeCardId,
            LocalDate workDate,
            ExceptionType exceptionType,
            ExceptionSeverity severity,
            String detailsJson,
            ExceptionStatus status,
            UUID resolvedBy,
            Instant resolvedAt,
            String resolutionNote,
            Long version) {
    }

    public record ResolutionRequest(
            @NotNull ExceptionStatus status,
            @Size(max = 500) String resolutionNote) {
    }
}

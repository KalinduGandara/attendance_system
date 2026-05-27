package com.attendance.organization.web;

import com.attendance.organization.domain.EmployeeImportStatus;

import java.time.Instant;
import java.util.UUID;

public final class EmployeeImportDtos {

    private EmployeeImportDtos() {
    }

    public record ImportJobResponse(
            UUID id,
            EmployeeImportStatus status,
            String fileName,
            int totalRows,
            int processedRows,
            int createdCount,
            int updatedCount,
            int errorCount,
            String errorReport,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {
    }
}

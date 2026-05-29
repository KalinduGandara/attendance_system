package com.attendance.report.web;

import com.attendance.report.domain.ReportJob;
import com.attendance.report.domain.ReportStatus;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportParameters;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record RunReportRequest(
            @NotNull ReportType reportType,
            ReportParameters parameters) {
    }

    public record ReportJobResponse(
            UUID id,
            ReportType reportType,
            ReportStatus status,
            Long rowCount,
            String errorMessage,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            String downloadUrl) {

        public static ReportJobResponse from(ReportJob j) {
            String downloadUrl = j.getStatus() == ReportStatus.DONE
                    ? "/api/v1/reports/" + j.getId() + "/download"
                    : null;
            return new ReportJobResponse(
                    j.getId(), j.getReportType(), j.getStatus(), j.getRowCount(),
                    j.getErrorMessage(), j.getStartedAt(), j.getCompletedAt(),
                    j.getCreatedAt(), downloadUrl);
        }
    }
}

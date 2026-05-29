package com.attendance.report.domain;

import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the asynchronous generation of a single report. Mirrors the
 * {@code employee_import_job} pattern: a plain job-tracking table (not an
 * audited business entity), with status + result fields persisted so the job
 * survives a backend restart.
 */
@Entity
@Table(name = "report_job")
@Getter
@Setter
@NoArgsConstructor
public class ReportJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Column(name = "parameters_json", columnDefinition = "TEXT", nullable = false)
    private String parametersJson;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "requested_by", columnDefinition = "BINARY(16)")
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReportStatus status = ReportStatus.QUEUED;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}

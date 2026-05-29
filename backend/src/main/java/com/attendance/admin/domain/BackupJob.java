package com.attendance.admin.domain;

import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks one database backup run. A plain job-tracking table (like
 * {@code report_job}/{@code employee_import_job}) rather than an audited
 * business entity: all state is persisted so a run survives a backend restart
 * and is inspectable from the Backups page.
 */
@Entity
@Table(name = "backup_job")
@Getter
@Setter
@NoArgsConstructor
public class BackupJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16)
    private BackupTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BackupStatus status = BackupStatus.RUNNING;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}

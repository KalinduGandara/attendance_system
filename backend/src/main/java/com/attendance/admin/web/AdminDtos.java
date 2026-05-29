package com.attendance.admin.web;

import com.attendance.admin.domain.BackupJob;
import com.attendance.admin.domain.BackupStatus;
import com.attendance.admin.domain.BackupTrigger;
import com.attendance.admin.domain.RetentionPolicy;
import com.attendance.admin.domain.SettingType;
import com.attendance.admin.domain.SystemSetting;
import com.attendance.common.audit.AuditEvent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Request/response DTOs for the System Admin endpoints. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---------- audit ----------

    public record AuditEventResponse(
            UUID id,
            UUID actorUserId,
            String actorUsername,
            String action,
            String entityType,
            UUID entityId,
            String beforeJson,
            String afterJson,
            String ip,
            String userAgent,
            String requestId,
            Instant occurredAt) {

        public static AuditEventResponse from(AuditEvent e) {
            return new AuditEventResponse(e.getId(), e.getActorUserId(), e.getActorUsername(),
                    e.getAction(), e.getEntityType(), e.getEntityId(), e.getBeforeJson(),
                    e.getAfterJson(), e.getIp(), e.getUserAgent(), e.getRequestId(), e.getOccurredAt());
        }
    }

    // ---------- system settings ----------

    public record SystemSettingResponse(
            String key,
            String value,
            SettingType valueType,
            String description,
            Instant updatedAt) {

        public static SystemSettingResponse from(SystemSetting s) {
            return new SystemSettingResponse(s.getSettingKey(), s.getSettingValue(),
                    s.getValueType(), s.getDescription(), s.getUpdatedAt());
        }
    }

    // ---------- backups ----------

    public record BackupJobResponse(
            UUID id,
            BackupTrigger triggerType,
            BackupStatus status,
            Long sizeBytes,
            Instant startedAt,
            Instant completedAt,
            String errorMessage,
            String downloadUrl,
            Instant createdAt) {

        public static BackupJobResponse from(BackupJob j) {
            String downloadUrl = j.getStatus() == BackupStatus.DONE
                    ? "/api/v1/system/backups/" + j.getId() + "/download" : null;
            return new BackupJobResponse(j.getId(), j.getTriggerType(), j.getStatus(),
                    j.getSizeBytes(), j.getStartedAt(), j.getCompletedAt(), j.getErrorMessage(),
                    downloadUrl, j.getCreatedAt());
        }
    }

    // ---------- retention ----------

    public record RetentionPolicyResponse(
            UUID id,
            String entityType,
            int retainDays,
            boolean enabled,
            Instant lastRunAt,
            Long lastRunDeleted,
            Instant updatedAt) {

        public static RetentionPolicyResponse from(RetentionPolicy p) {
            return new RetentionPolicyResponse(p.getId(), p.getEntityType(), p.getRetainDays(),
                    p.isEnabled(), p.getLastRunAt(), p.getLastRunDeleted(), p.getUpdatedAt());
        }
    }

    public record UpdateRetentionPolicyRequest(
            @NotNull @Min(1) Integer retainDays,
            @NotNull Boolean enabled) {
    }
}

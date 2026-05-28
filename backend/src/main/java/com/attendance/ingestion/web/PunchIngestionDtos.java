package com.attendance.ingestion.web;

import com.attendance.device.domain.CredentialType;
import com.attendance.timecard.domain.PunchEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PunchIngestionDtos {

    private PunchIngestionDtos() {
    }

    public record CredentialRef(
            @NotNull CredentialType type,
            @Size(max = 255) String value) {
    }

    public record PunchEventRequest(
            @Size(min = 1, max = 128) String externalEventId,
            @NotNull PunchEventType eventType,
            @NotNull Instant eventTime,
            @Valid CredentialRef credential,
            UUID deviceId,
            UUID employeeId,
            Map<String, Object> rawPayload) {
    }

    public record PunchBatchRequest(
            @NotNull UUID sourceId,
            @NotEmpty @Valid List<PunchEventRequest> events) {
    }

    public record EventResult(
            String externalEventId,
            String status,
            UUID punchEventId,
            String detail) {
    }

    public record IngestionResponse(
            int accepted,
            int duplicate,
            int unresolved,
            int invalid,
            List<EventResult> results) {
    }
}

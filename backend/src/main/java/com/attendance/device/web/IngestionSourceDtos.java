package com.attendance.device.web;

import com.attendance.device.domain.IngestionSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class IngestionSourceDtos {

    private IngestionSourceDtos() {
    }

    public record IngestionSourceRequest(
            @NotBlank @Size(max = 128) String name,
            @NotNull IngestionSourceType sourceType,
            Boolean enabled,
            Map<String, Object> config) {
    }

    public record IngestionSourceResponse(
            UUID id,
            String name,
            IngestionSourceType sourceType,
            boolean enabled,
            Map<String, Object> config,
            boolean apiKeyConfigured,
            Instant lastEventAt,
            long eventsTotal,
            long eventsRejected,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    /** Returned exactly once — on create or rotate. The plaintext key is never persisted. */
    public record IngestionSourceWithKey(
            IngestionSourceResponse source,
            String apiKey) {
    }
}

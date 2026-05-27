package com.attendance.device.web;

import com.attendance.device.domain.DeviceStatus;
import com.attendance.device.domain.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 128) String name,
            @NotNull DeviceType deviceType,
            @Size(max = 255) String location,
            DeviceStatus status,
            Map<String, Object> capabilities) {
    }

    public record DeviceResponse(
            UUID id,
            String name,
            DeviceType deviceType,
            String location,
            DeviceStatus status,
            Map<String, Object> capabilities,
            Instant lastSeenAt,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }
}

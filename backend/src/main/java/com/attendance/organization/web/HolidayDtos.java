package com.attendance.organization.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HolidayDtos {

    private HolidayDtos() {
    }

    public record HolidayRequest(
            @NotBlank @Size(max = 128) String name,
            @NotNull LocalDate holidayDate,
            boolean recurringYearly,
            boolean paid,
            @Size(max = 255) String description,
            Set<UUID> groupIds) {
    }

    public record HolidayResponse(
            UUID id,
            String name,
            LocalDate holidayDate,
            boolean recurringYearly,
            boolean paid,
            String description,
            List<GroupRef> groups,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    public record GroupRef(UUID id, String name) {
    }
}

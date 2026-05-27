package com.attendance.organization.web;

import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.domain.CustomFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomFieldDtos {

    private CustomFieldDtos() {
    }

    public record CustomFieldDefinitionRequest(
            @NotNull CustomFieldEntityType entityType,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "must be lowercase snake_case")
            String fieldKey,
            @NotBlank @Size(max = 128) String displayLabel,
            @NotNull CustomFieldType fieldType,
            boolean required,
            List<String> options,
            @PositiveOrZero int displayOrder) {
    }

    public record CustomFieldDefinitionResponse(
            UUID id,
            CustomFieldEntityType entityType,
            String fieldKey,
            String displayLabel,
            CustomFieldType fieldType,
            boolean required,
            List<String> options,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }
}

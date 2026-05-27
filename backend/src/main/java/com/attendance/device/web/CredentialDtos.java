package com.attendance.device.web;

import com.attendance.device.domain.CredentialStatus;
import com.attendance.device.domain.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CredentialDtos {

    private CredentialDtos() {
    }

    public record CredentialRequest(
            @NotNull CredentialType credentialType,
            @NotBlank @Size(max = 255) String value,
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            CredentialStatus status) {
    }

    public record CredentialResponse(
            UUID id,
            UUID employeeId,
            CredentialType credentialType,
            String valueMasked,
            LocalDate validFrom,
            LocalDate validTo,
            CredentialStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }
}

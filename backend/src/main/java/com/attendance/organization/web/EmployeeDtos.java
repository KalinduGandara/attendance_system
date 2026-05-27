package com.attendance.organization.web;

import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record EmployeeRequest(
            @NotBlank @Size(max = 64) String employeeCode,
            @NotBlank @Size(max = 64) String firstName,
            @NotBlank @Size(max = 64) String lastName,
            @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            UUID departmentId,
            UUID managerId,
            UUID userId,
            @NotNull EmploymentType employmentType,
            @NotNull LocalDate hireDate,
            LocalDate terminationDate,
            @Size(max = 64) String timezone,
            EmployeeStatus status,
            Set<UUID> groupIds,
            Map<String, Object> customFields) {
    }

    public record CustomFieldValueDto(
            UUID definitionId,
            String fieldKey,
            String displayLabel,
            String fieldType,
            String stringValue,
            BigDecimal numberValue,
            LocalDate dateValue,
            Boolean booleanValue) {
    }

    public record EmployeeResponse(
            UUID id,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String phone,
            UUID departmentId,
            String departmentName,
            UUID managerId,
            String managerName,
            UUID userId,
            String username,
            EmploymentType employmentType,
            LocalDate hireDate,
            LocalDate terminationDate,
            String timezone,
            EmployeeStatus status,
            List<GroupRef> groups,
            List<CustomFieldValueDto> customFields,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    public record GroupRef(UUID id, String name) {
    }

    public record EmployeeSummary(
            UUID id,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            UUID departmentId,
            String departmentName,
            EmployeeStatus status,
            EmploymentType employmentType,
            LocalDate hireDate) {
    }
}

package com.attendance.organization.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record DepartmentRequest(
            @NotBlank @Size(max = 128) String name,
            UUID parentId,
            @Size(max = 64) String timezone) {
    }

    public record DepartmentResponse(
            UUID id,
            String name,
            UUID parentId,
            String timezone,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    public record DepartmentNode(
            UUID id,
            String name,
            String timezone,
            List<DepartmentNode> children) {
    }
}

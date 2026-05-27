package com.attendance.organization.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserGroupDtos {

    private UserGroupDtos() {
    }

    public record UserGroupRequest(
            @NotBlank @Size(max = 128) String name,
            UUID parentId,
            @Size(max = 255) String description) {
    }

    public record UserGroupResponse(
            UUID id,
            String name,
            UUID parentId,
            String description,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    public record UserGroupNode(
            UUID id,
            String name,
            String description,
            List<UserGroupNode> children) {
    }
}

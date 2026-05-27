package com.attendance.identity.web;

import com.attendance.identity.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 128) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password,
            UserStatus status,
            @NotEmpty Set<UUID> roleIds) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 128) String displayName,
            UserStatus status,
            @NotEmpty Set<UUID> roleIds) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            UserStatus status,
            List<RoleRef> roles,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
    }

    public record RoleRef(UUID id, String name) {
    }
}

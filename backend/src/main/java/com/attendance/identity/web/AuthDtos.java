package com.attendance.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record UserInfo(UUID id, String username, String displayName,
                           List<String> roles, List<String> permissions) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {
    }
}

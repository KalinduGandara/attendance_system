package com.attendance.identity.web;

import com.attendance.common.web.PageResponse;
import com.attendance.identity.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.read')")
    @Operation(summary = "List users")
    public PageResponse<UserAdminDtos.UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "username") String sort) {
        return service.list(page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.read')")
    @Operation(summary = "Get a user by id")
    public UserAdminDtos.UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user.write')")
    @Operation(summary = "Create a user")
    public ResponseEntity<UserAdminDtos.UserResponse> create(
            @Valid @RequestBody UserAdminDtos.CreateUserRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user.write')")
    @Operation(summary = "Update a user")
    public UserAdminDtos.UserResponse update(@PathVariable UUID id,
                                             @Valid @RequestBody UserAdminDtos.UpdateUserRequest body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user.write')")
    @Operation(summary = "Reset a user's password (admin action)")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @Valid @RequestBody UserAdminDtos.ResetPasswordRequest body) {
        service.resetPassword(id, body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user.write')")
    @Operation(summary = "Delete a user")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

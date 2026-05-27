package com.attendance.identity.web;

import com.attendance.identity.domain.Permission;
import com.attendance.identity.repository.PermissionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions")
public class PermissionController {

    private final PermissionRepository permissionRepository;

    public PermissionController(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role.read')")
    @Operation(summary = "List the full seeded permission catalog")
    public List<PermissionResponse> list() {
        return permissionRepository.findAll(Sort.by("code")).stream()
                .map(this::toResponse)
                .toList();
    }

    private PermissionResponse toResponse(Permission p) {
        return new PermissionResponse(p.getId(), p.getCode(), p.getDescription());
    }

    public record PermissionResponse(UUID id, String code, String description) {
    }
}

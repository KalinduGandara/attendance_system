package com.attendance.identity.web;

import com.attendance.identity.domain.Permission;
import com.attendance.identity.domain.Role;
import com.attendance.identity.repository.RoleRepository;
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
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role.read')")
    @Operation(summary = "List all roles with their permissions")
    public List<RoleResponse> list() {
        return roleRepository.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    private RoleResponse toResponse(Role r) {
        List<String> perms = r.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();
        return new RoleResponse(r.getId(), r.getName(), r.getDescription(), r.isSystem(), perms);
    }

    public record RoleResponse(UUID id, String name, String description, boolean system,
                               List<String> permissions) {
    }
}

package com.attendance.admin.web;

import com.attendance.admin.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/settings")
@Tag(name = "System settings")
@PreAuthorize("hasAuthority('system.admin')")
public class SystemSettingController {

    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all org-wide settings")
    public List<AdminDtos.SystemSettingResponse> list() {
        return service.getAll().stream().map(AdminDtos.SystemSettingResponse::from).toList();
    }

    @PatchMapping
    @Operation(summary = "Update a partial map of settings (type-validated, audited)")
    public List<AdminDtos.SystemSettingResponse> update(@RequestBody Map<String, String> changes) {
        return service.update(changes).stream().map(AdminDtos.SystemSettingResponse::from).toList();
    }
}

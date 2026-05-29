package com.attendance.admin.web;

import com.attendance.admin.service.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/retention-policies")
@Tag(name = "Retention policies")
@PreAuthorize("hasAuthority('system.admin')")
public class RetentionController {

    private final RetentionService service;

    public RetentionController(RetentionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List retention policies")
    public List<AdminDtos.RetentionPolicyResponse> list() {
        return service.getPolicies().stream().map(AdminDtos.RetentionPolicyResponse::from).toList();
    }

    @PutMapping("/{entityType}")
    @Operation(summary = "Update a retention policy (window + enable flag)")
    public AdminDtos.RetentionPolicyResponse update(
            @PathVariable String entityType,
            @Valid @RequestBody AdminDtos.UpdateRetentionPolicyRequest body) {
        return AdminDtos.RetentionPolicyResponse.from(
                service.updatePolicy(entityType, body.retainDays(), body.enabled()));
    }
}

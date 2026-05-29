package com.attendance.admin.web;

import com.attendance.admin.service.AuditQueryService;
import com.attendance.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit log")
@PreAuthorize("hasAuthority('audit.read')")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search the audit trail (newest first)")
    public PageResponse<AdminDtos.AuditEventResponse> search(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(
                service.search(actorUserId, action, entityType, entityId, from, to, page, size),
                AdminDtos.AuditEventResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single audit event (before/after detail)")
    public AdminDtos.AuditEventResponse get(@PathVariable UUID id) {
        return AdminDtos.AuditEventResponse.from(service.get(id));
    }
}

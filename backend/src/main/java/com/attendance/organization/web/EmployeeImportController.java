package com.attendance.organization.web;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.EmployeeImportJob;
import com.attendance.organization.service.EmployeeImportService;
import com.attendance.platform.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees/imports")
@Tag(name = "Employee Imports")
public class EmployeeImportController {

    private final EmployeeImportService service;

    public EmployeeImportController(EmployeeImportService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Bulk-import employees from a CSV file. Sync for <1000 rows, async otherwise.")
    public ResponseEntity<EmployeeImportDtos.ImportJobResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AppPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "CSV file is required");
        }
        UUID requestedBy = principal == null ? null : principal.userId();
        EmployeeImportJob job = service.submit(file, requestedBy);
        HttpStatus status = switch (job.getStatus()) {
            case QUEUED, RUNNING -> HttpStatus.ACCEPTED;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(toResponse(job));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "Get status / result of a bulk import job")
    public EmployeeImportDtos.ImportJobResponse get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    private EmployeeImportDtos.ImportJobResponse toResponse(EmployeeImportJob j) {
        return new EmployeeImportDtos.ImportJobResponse(
                j.getId(), j.getStatus(), j.getFileName(),
                j.getTotalRows(), j.getProcessedRows(),
                j.getCreatedCount(), j.getUpdatedCount(), j.getErrorCount(),
                j.getErrorReport(), j.getErrorMessage(),
                j.getStartedAt(), j.getCompletedAt());
    }
}

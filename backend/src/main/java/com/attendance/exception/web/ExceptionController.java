package com.attendance.exception.web;

import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.service.ExceptionEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exceptions")
@Tag(name = "Exceptions")
public class ExceptionController {

    private final ExceptionEventService service;

    public ExceptionController(ExceptionEventService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('exception.read')")
    @Operation(summary = "List exceptions filtered by employee/status/date window.")
    public List<ExceptionDtos.ExceptionEventResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) ExceptionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.search(employeeId, status, from, to);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('exception.resolve')")
    @Operation(summary = "Resolve or ignore an exception.")
    public ExceptionDtos.ExceptionEventResponse resolve(@PathVariable UUID id,
                                                        @Valid @RequestBody
                                                        ExceptionDtos.ResolutionRequest body) {
        return service.resolve(id, body);
    }
}

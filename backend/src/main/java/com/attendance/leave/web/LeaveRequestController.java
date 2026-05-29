package com.attendance.leave.web;

import com.attendance.leave.domain.LeaveRequestStatus;
import com.attendance.leave.service.LeaveRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leave-requests")
@Tag(name = "Leave requests")
public class LeaveRequestController {

    private final LeaveRequestService service;

    public LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('leave.read')")
    @Operation(summary = "List leave requests with optional filters.")
    public List<LeaveDtos.LeaveRequestResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(employeeId, status, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('leave.read')")
    public LeaveDtos.LeaveRequestResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('leave.read')")
    public ResponseEntity<LeaveDtos.LeaveRequestResponse> create(
            @Valid @RequestBody LeaveDtos.LeaveRequestRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('leave.approve')")
    public LeaveDtos.LeaveRequestResponse approve(@PathVariable UUID id,
                                                  @RequestBody(required = false)
                                                  LeaveDtos.LeaveDecision decision) {
        return service.approve(id, decision);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('leave.approve')")
    public LeaveDtos.LeaveRequestResponse reject(@PathVariable UUID id,
                                                 @RequestBody(required = false)
                                                 LeaveDtos.LeaveDecision decision) {
        return service.reject(id, decision);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('leave.read')")
    public LeaveDtos.LeaveRequestResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}

package com.attendance.timecard.web;

import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.service.PunchEventReadService;
import com.attendance.timecard.service.UnresolvedPunchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/punch-events")
@Tag(name = "Punch events")
public class PunchEventController {

    private final PunchEventReadService readService;
    private final UnresolvedPunchService unresolvedPunchService;

    public PunchEventController(PunchEventReadService readService,
                                UnresolvedPunchService unresolvedPunchService) {
        this.readService = readService;
        this.unresolvedPunchService = unresolvedPunchService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('timecard.read')")
    @Operation(summary = "List raw punch events; supports filtering by employee/status/time window.")
    public List<TimeCardDtos.PunchEventResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) PunchEventStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return readService.list(employeeId, status, from, to, page, size);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('timecard.edit')")
    @Operation(summary = "Assign an UNRESOLVED punch to an employee. Re-runs the day's recompute.")
    public TimeCardDtos.PunchEventResponse assign(@PathVariable UUID id,
                                                  @Valid @RequestBody AssignRequest body) {
        var saved = unresolvedPunchService.assignToEmployee(id, body.employeeId());
        return new TimeCardDtos.PunchEventResponse(
                saved.getId(),
                saved.getEmployeeId(),
                saved.getDeviceId(),
                saved.getIngestionSourceId(),
                saved.getExternalEventId(),
                saved.getEventType(),
                saved.getEventTimeUtc(),
                saved.getStatus(),
                saved.getProcessedAt());
    }

    public record AssignRequest(@NotNull UUID employeeId) {
    }
}

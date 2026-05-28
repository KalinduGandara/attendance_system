package com.attendance.timecard.web;

import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.service.PunchEventReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    public PunchEventController(PunchEventReadService readService) {
        this.readService = readService;
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
}

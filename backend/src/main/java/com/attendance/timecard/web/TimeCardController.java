package com.attendance.timecard.web;

import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.service.TimeCardReadService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timecards")
@Tag(name = "Time cards")
public class TimeCardController {

    private final TimeCardReadService readService;

    public TimeCardController(TimeCardReadService readService) {
        this.readService = readService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('timecard.read')")
    @Operation(summary = "List daily time cards. Phase 5 ships a basic filter; Phase 6 adds group + status filters.")
    public List<TimeCardDtos.TimeCardResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) DailyTimeCardStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return readService.list(employeeId, status, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('timecard.read')")
    @Operation(summary = "Get a daily time card by id")
    public TimeCardDtos.TimeCardResponse get(@PathVariable UUID id) {
        return readService.get(id);
    }
}

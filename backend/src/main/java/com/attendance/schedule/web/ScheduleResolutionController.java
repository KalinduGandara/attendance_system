package com.attendance.schedule.web;

import com.attendance.schedule.service.ScheduleResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule-resolution")
@Tag(name = "Schedule resolution")
public class ScheduleResolutionController {

    /** Cap the range to keep range-resolve cheap; the time-card engine
     *  recomputes day-by-day anyway. */
    private static final int MAX_RANGE_DAYS = 90;

    private final ScheduleResolver resolver;

    public ScheduleResolutionController(ScheduleResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "Resolve the effective shift for an employee on a date or date range")
    public List<ScheduleDtos.ResolvedScheduleResponse> resolve(
            @RequestParam UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? from : to;
        if (end.isBefore(from)) {
            end = from;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            end = from.plusDays(MAX_RANGE_DAYS - 1);
        }
        List<ScheduleDtos.ResolvedScheduleResponse> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            out.add(ScheduleDtos.ResolvedScheduleResponse.from(resolver.resolve(employeeId, d)));
        }
        return out;
    }
}

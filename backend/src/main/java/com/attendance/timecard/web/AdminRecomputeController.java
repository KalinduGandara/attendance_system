package com.attendance.timecard.web;

import com.attendance.common.error.ApiException;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.timecard.service.TimeCardRecomputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Operator endpoint for forcing a recompute over a date range. Documented in
 * runbook.md §6 ("Force time-card recompute").
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminRecomputeController {

    private final TimeCardRecomputeService recomputeService;
    private final EmployeeRepository employeeRepository;

    public AdminRecomputeController(TimeCardRecomputeService recomputeService,
                                    EmployeeRepository employeeRepository) {
        this.recomputeService = recomputeService;
        this.employeeRepository = employeeRepository;
    }

    @PostMapping("/recompute")
    @PreAuthorize("hasAuthority('timecard.edit')")
    @Operation(summary = "Force recompute of time cards over a date range")
    public TimeCardDtos.RecomputeResponse recompute(@RequestBody TimeCardDtos.RecomputeRequest req) {
        if (req.from() == null || req.to() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "from and to are required");
        }
        if (req.from().isAfter(req.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "from must be on or before to");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(req.from(), req.to()) > 92) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Recompute window cannot exceed 92 days");
        }

        List<UUID> ids = req.employeeIds() == null || req.employeeIds().isEmpty()
                ? employeeRepository.findActiveEmployeeIds()
                : req.employeeIds();
        int count = 0;
        for (UUID id : ids) {
            for (LocalDate d = req.from(); !d.isAfter(req.to()); d = d.plusDays(1)) {
                recomputeService.recompute(id, d);
                count++;
            }
        }
        return new TimeCardDtos.RecomputeResponse(count);
    }
}

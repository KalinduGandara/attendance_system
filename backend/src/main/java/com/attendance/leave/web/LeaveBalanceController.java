package com.attendance.leave.web;

import com.attendance.leave.service.LeaveBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/employees/{employeeId}/leave-balances")
@Tag(name = "Leave balances")
public class LeaveBalanceController {

    private final LeaveBalanceService service;

    public LeaveBalanceController(LeaveBalanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('leave.read')")
    @Operation(summary = "List leave balances for an employee (default = current year).")
    public List<LeaveDtos.BalanceResponse> list(@PathVariable UUID employeeId,
                                                @RequestParam(required = false) Integer year) {
        int y = year != null ? year : LocalDate.now().getYear();
        return service.listForEmployee(employeeId, y);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('leave.approve')")
    @Operation(summary = "Upsert an explicit balance for (employee, type, year). Used by HR.")
    public LeaveDtos.BalanceResponse adjust(@PathVariable UUID employeeId,
                                            @Valid @RequestBody LeaveDtos.BalanceAdjustment body) {
        return service.adjust(employeeId, body);
    }
}

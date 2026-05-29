package com.attendance.leave.web;

import com.attendance.leave.domain.HalfDayPart;
import com.attendance.leave.domain.LeaveRequestStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeaveDtos {

    private LeaveDtos() {
    }

    public record LeaveTypeRequest(
            @NotBlank @Size(max = 128) String name,
            @NotNull UUID timeCodeId,
            @NotNull @DecimalMin("0") @DecimalMax("365") BigDecimal defaultAnnualDays,
            boolean requiresApproval,
            String accrualRuleJson,
            boolean active) {
    }

    public record LeaveTypeResponse(
            UUID id,
            UUID timeCodeId,
            String timeCodeCode,
            String name,
            BigDecimal defaultAnnualDays,
            boolean requiresApproval,
            String accrualRuleJson,
            boolean active,
            Long version) {
    }

    public record BalanceAdjustment(
            @NotNull UUID leaveTypeId,
            @NotNull Integer year,
            @NotNull BigDecimal balanceDays) {
    }

    public record BalanceResponse(
            UUID id,
            UUID employeeId,
            UUID leaveTypeId,
            String leaveTypeName,
            int year,
            BigDecimal balanceDays) {
    }

    public record LeaveRequestRequest(
            @NotNull UUID employeeId,
            @NotNull UUID leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            boolean halfDay,
            HalfDayPart halfDayPart,
            @Size(max = 500) String reason,
            boolean retroactive) {
    }

    public record LeaveDecision(
            @Size(max = 500) String rejectionReason) {
    }

    public record LeaveRequestResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID leaveTypeId,
            String leaveTypeName,
            LocalDate startDate,
            LocalDate endDate,
            boolean halfDay,
            HalfDayPart halfDayPart,
            String reason,
            LeaveRequestStatus status,
            boolean retroactive,
            UUID approvedBy,
            Instant approvedAt,
            String rejectionReason,
            BigDecimal daysRequested,
            Long version) {
    }
}

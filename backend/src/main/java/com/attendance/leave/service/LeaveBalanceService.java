package com.attendance.leave.service;

import com.attendance.common.error.ApiException;
import com.attendance.leave.domain.LeaveBalance;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveBalanceRepository;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.leave.web.LeaveDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveTypeRepository typeRepository;

    public LeaveBalanceService(LeaveBalanceRepository balanceRepository,
                               LeaveTypeRepository typeRepository) {
        this.balanceRepository = balanceRepository;
        this.typeRepository = typeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveDtos.BalanceResponse> listForEmployee(UUID employeeId, int year) {
        List<LeaveBalance> rows = balanceRepository.findByEmployeeIdAndYearOrderByCreatedAtAsc(
                employeeId, year);
        Map<UUID, String> names = typeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));
        return rows.stream()
                .map(b -> new LeaveDtos.BalanceResponse(
                        b.getId(),
                        b.getEmployeeId(),
                        b.getLeaveTypeId(),
                        names.get(b.getLeaveTypeId()),
                        b.getYear(),
                        b.getBalanceDays()))
                .toList();
    }

    /**
     * Upsert the balance for (employee, type, year). Used by admin/HR adjust UI
     * and by year-end seeding.
     */
    @Transactional
    public LeaveDtos.BalanceResponse adjust(UUID employeeId, LeaveDtos.BalanceAdjustment req) {
        LeaveType type = typeRepository.findById(req.leaveTypeId()).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "validation", "Unknown leave type"));
        LeaveBalance b = balanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, req.leaveTypeId(), req.year())
                .orElseGet(() -> {
                    LeaveBalance fresh = new LeaveBalance();
                    fresh.setEmployeeId(employeeId);
                    fresh.setLeaveTypeId(req.leaveTypeId());
                    fresh.setYear(req.year());
                    return fresh;
                });
        b.setBalanceDays(req.balanceDays());
        LeaveBalance saved = balanceRepository.saveAndFlush(b);
        return new LeaveDtos.BalanceResponse(
                saved.getId(),
                saved.getEmployeeId(),
                saved.getLeaveTypeId(),
                type.getName(),
                saved.getYear(),
                saved.getBalanceDays());
    }

    /**
     * Deduct {@code days} from the (employee, type, year) balance. Lazy-creates
     * a balance row at {@code defaultAnnualDays} when none exists. Allows
     * negative balances — over-drawing is an operational decision the UI surfaces.
     */
    @Transactional
    public LeaveBalance deduct(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days) {
        LeaveType type = typeRepository.findById(leaveTypeId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "validation", "Unknown leave type"));
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year, type);
        b.setBalanceDays(b.getBalanceDays().subtract(days));
        return balanceRepository.saveAndFlush(b);
    }

    /**
     * Inverse of {@link #deduct} — restores days to the balance when a request
     * is cancelled after approval.
     */
    @Transactional
    public LeaveBalance refund(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days) {
        LeaveType type = typeRepository.findById(leaveTypeId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "validation", "Unknown leave type"));
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year, type);
        b.setBalanceDays(b.getBalanceDays().add(days));
        return balanceRepository.saveAndFlush(b);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> currentBalance(UUID employeeId, UUID leaveTypeId, int year) {
        return balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .map(LeaveBalance::getBalanceDays);
    }

    private LeaveBalance ensureBalance(UUID employeeId, UUID leaveTypeId, int year, LeaveType type) {
        return balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElseGet(() -> {
                    LeaveBalance fresh = new LeaveBalance();
                    fresh.setEmployeeId(employeeId);
                    fresh.setLeaveTypeId(leaveTypeId);
                    fresh.setYear(year);
                    fresh.setBalanceDays(type.getDefaultAnnualDays());
                    return balanceRepository.saveAndFlush(fresh);
                });
    }
}

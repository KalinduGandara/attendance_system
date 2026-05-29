package com.attendance.leave.service;

import com.attendance.common.audit.RequestContext;
import com.attendance.common.error.ApiException;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestStatus;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.leave.web.LeaveDtos;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.timecard.service.TimeCardRecomputeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lifecycle service for {@link LeaveRequest}. Owns approve/reject/cancel
 * transitions, calls {@link LeaveBalanceService} to debit/refund balances,
 * and triggers a bounded recompute of the time cards in the affected window
 * so an approved leave that covers an {@code ABSENT} day flips it to
 * {@code LEAVE} and closes the absent exception.
 */
@Service
public class LeaveRequestService {

    private final LeaveRequestRepository repository;
    private final LeaveTypeRepository typeRepository;
    private final LeaveBalanceService balanceService;
    private final EmployeeRepository employeeRepository;
    private final TimeCardRecomputeService recomputeService;

    public LeaveRequestService(LeaveRequestRepository repository,
                               LeaveTypeRepository typeRepository,
                               LeaveBalanceService balanceService,
                               EmployeeRepository employeeRepository,
                               TimeCardRecomputeService recomputeService) {
        this.repository = repository;
        this.typeRepository = typeRepository;
        this.balanceService = balanceService;
        this.employeeRepository = employeeRepository;
        this.recomputeService = recomputeService;
    }

    @Transactional(readOnly = true)
    public List<LeaveDtos.LeaveRequestResponse> list(UUID employeeId,
                                                     LeaveRequestStatus status,
                                                     LocalDate from,
                                                     LocalDate to) {
        List<LeaveRequest> rows = repository.search(employeeId, status, from, to);
        return enrich(rows);
    }

    @Transactional(readOnly = true)
    public LeaveDtos.LeaveRequestResponse get(UUID id) {
        LeaveRequest r = repository.findById(id).orElseThrow(this::notFound);
        return enrich(List.of(r)).get(0);
    }

    @Transactional
    public LeaveDtos.LeaveRequestResponse create(LeaveDtos.LeaveRequestRequest req) {
        validateDates(req.startDate(), req.endDate(), req.halfDay());
        LeaveType type = typeRepository.findById(req.leaveTypeId()).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "validation", "Unknown leave type"));
        if (!type.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Leave type is inactive");
        }
        if (req.halfDay() && req.halfDayPart() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "halfDayPart is required when halfDay = true");
        }

        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(req.employeeId());
        r.setLeaveTypeId(req.leaveTypeId());
        r.setStartDate(req.startDate());
        r.setEndDate(req.endDate());
        r.setHalfDay(req.halfDay());
        r.setHalfDayPart(req.halfDayPart());
        r.setReason(req.reason());
        r.setRetroactive(req.retroactive());

        if (type.isRequiresApproval()) {
            r.setStatus(LeaveRequestStatus.PENDING);
        } else {
            r.setStatus(LeaveRequestStatus.APPROVED);
            r.setApprovedAt(Instant.now());
            r.setApprovedBy(RequestContext.actorUserId().orElse(null));
        }

        LeaveRequest saved = repository.saveAndFlush(r);

        if (saved.getStatus() == LeaveRequestStatus.APPROVED) {
            applyApprovalSideEffects(saved);
        }
        return enrich(List.of(saved)).get(0);
    }

    @Transactional
    public LeaveDtos.LeaveRequestResponse approve(UUID id, LeaveDtos.LeaveDecision decision) {
        LeaveRequest r = repository.findById(id).orElseThrow(this::notFound);
        if (r.getStatus() != LeaveRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Only PENDING requests can be approved");
        }
        r.setStatus(LeaveRequestStatus.APPROVED);
        r.setApprovedAt(Instant.now());
        r.setApprovedBy(RequestContext.actorUserId().orElse(null));
        r.setRejectionReason(null);
        LeaveRequest saved = repository.saveAndFlush(r);
        applyApprovalSideEffects(saved);
        return enrich(List.of(saved)).get(0);
    }

    @Transactional
    public LeaveDtos.LeaveRequestResponse reject(UUID id, LeaveDtos.LeaveDecision decision) {
        LeaveRequest r = repository.findById(id).orElseThrow(this::notFound);
        if (r.getStatus() != LeaveRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Only PENDING requests can be rejected");
        }
        r.setStatus(LeaveRequestStatus.REJECTED);
        r.setRejectionReason(decision == null ? null : decision.rejectionReason());
        r.setApprovedAt(Instant.now());
        r.setApprovedBy(RequestContext.actorUserId().orElse(null));
        LeaveRequest saved = repository.saveAndFlush(r);
        return enrich(List.of(saved)).get(0);
    }

    @Transactional
    public LeaveDtos.LeaveRequestResponse cancel(UUID id) {
        LeaveRequest r = repository.findById(id).orElseThrow(this::notFound);
        if (r.getStatus() == LeaveRequestStatus.CANCELLED
                || r.getStatus() == LeaveRequestStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Request is already terminal");
        }
        boolean wasApproved = r.getStatus() == LeaveRequestStatus.APPROVED;
        r.setStatus(LeaveRequestStatus.CANCELLED);
        LeaveRequest saved = repository.saveAndFlush(r);
        if (wasApproved) {
            // Refund the previously-debited balance and re-run recompute so the
            // affected days revert to their pre-leave state.
            BigDecimal days = computeDays(saved);
            balanceService.refund(saved.getEmployeeId(), saved.getLeaveTypeId(),
                    saved.getStartDate().getYear(), days);
            recomputeRange(saved.getEmployeeId(), saved.getStartDate(), saved.getEndDate());
        }
        return enrich(List.of(saved)).get(0);
    }

    private void applyApprovalSideEffects(LeaveRequest saved) {
        BigDecimal days = computeDays(saved);
        balanceService.deduct(saved.getEmployeeId(), saved.getLeaveTypeId(),
                saved.getStartDate().getYear(), days);
        recomputeRange(saved.getEmployeeId(), saved.getStartDate(), saved.getEndDate());
    }

    private void recomputeRange(UUID employeeId, LocalDate from, LocalDate to) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            recomputeService.recompute(employeeId, d);
        }
    }

    static BigDecimal computeDays(LeaveRequest r) {
        long inclusive = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()) + 1;
        BigDecimal whole = BigDecimal.valueOf(inclusive);
        return r.isHalfDay() ? whole.multiply(new BigDecimal("0.5")) : whole;
    }

    private void validateDates(LocalDate from, LocalDate to, boolean halfDay) {
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "startDate and endDate are required");
        }
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "endDate must be on or after startDate");
        }
        if (halfDay && !from.equals(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Half-day requests must cover a single day");
        }
    }

    private List<LeaveDtos.LeaveRequestResponse> enrich(List<LeaveRequest> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, LeaveType> typesById = typeRepository.findAllById(
                rows.stream().map(LeaveRequest::getLeaveTypeId).toList()).stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t));
        Map<UUID, Employee> employees = employeeRepository.findAllById(
                rows.stream().map(LeaveRequest::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        return rows.stream().map(r -> {
            LeaveType t = typesById.get(r.getLeaveTypeId());
            Employee e = employees.get(r.getEmployeeId());
            return new LeaveDtos.LeaveRequestResponse(
                    r.getId(),
                    r.getEmployeeId(),
                    e == null ? null : e.getFirstName() + " " + e.getLastName(),
                    r.getLeaveTypeId(),
                    t == null ? null : t.getName(),
                    r.getStartDate(),
                    r.getEndDate(),
                    r.isHalfDay(),
                    r.getHalfDayPart(),
                    r.getReason(),
                    r.getStatus(),
                    r.isRetroactive(),
                    r.getApprovedBy(),
                    r.getApprovedAt(),
                    r.getRejectionReason(),
                    computeDays(r),
                    r.getVersion());
        }).toList();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Leave request not found");
    }
}

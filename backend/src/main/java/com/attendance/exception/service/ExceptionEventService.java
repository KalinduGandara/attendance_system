package com.attendance.exception.service;

import com.attendance.common.audit.RequestContext;
import com.attendance.common.error.ApiException;
import com.attendance.exception.domain.ExceptionEvent;
import com.attendance.exception.domain.ExceptionSeverity;
import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.domain.ExceptionType;
import com.attendance.exception.repository.ExceptionEventRepository;
import com.attendance.exception.web.ExceptionDtos;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Internal-facing service used by the time-card engine to refresh the
 * exception list for an (employee, work_date) pair on each recompute.
 *
 * <p>On recompute we deliberately replace the OPEN exceptions for the day —
 * RESOLVED / IGNORED exceptions are preserved as part of the audit trail.
 */
@Service
public class ExceptionEventService {

    private final ExceptionEventRepository repository;
    private final EmployeeRepository employeeRepository;

    public ExceptionEventService(ExceptionEventRepository repository,
                                 EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void replaceForDay(UUID employeeId, LocalDate workDate, UUID dailyTimeCardId,
                              List<EmittedException> emitted) {
        repository.deleteOpenForEmployeeOnDate(employeeId, workDate);
        for (EmittedException e : emitted) {
            ExceptionEvent row = new ExceptionEvent();
            row.setEmployeeId(employeeId);
            row.setWorkDate(workDate);
            row.setDailyTimeCardId(dailyTimeCardId);
            row.setExceptionType(e.type());
            row.setSeverity(e.severity());
            row.setDetailsJson(e.detailsJson());
            row.setStatus(ExceptionStatus.OPEN);
            repository.save(row);
        }
    }

    @Transactional(readOnly = true)
    public List<ExceptionEvent> findForDay(UUID employeeId, LocalDate workDate) {
        return repository.findByEmployeeIdAndWorkDate(employeeId, workDate);
    }

    @Transactional(readOnly = true)
    public List<ExceptionDtos.ExceptionEventResponse> search(UUID employeeId,
                                                             ExceptionStatus status,
                                                             LocalDate from,
                                                             LocalDate to) {
        List<ExceptionEvent> rows = repository.search(employeeId, status, from, to);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Employee> employees = employeeRepository.findAllById(
                rows.stream().map(ExceptionEvent::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        return rows.stream().map(e -> toResponse(e, employees.get(e.getEmployeeId()))).toList();
    }

    @Transactional
    public ExceptionDtos.ExceptionEventResponse resolve(UUID id,
                                                        ExceptionDtos.ResolutionRequest req) {
        if (req.status() == ExceptionStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Cannot resolve to OPEN — use RESOLVED or IGNORED");
        }
        ExceptionEvent e = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Exception not found"));
        e.setStatus(req.status());
        e.setResolutionNote(req.resolutionNote());
        e.setResolvedBy(RequestContext.actorUserId().orElse(null));
        e.setResolvedAt(Instant.now());
        ExceptionEvent saved = repository.saveAndFlush(e);
        return toResponse(saved, employeeRepository.findById(saved.getEmployeeId()).orElse(null));
    }

    private ExceptionDtos.ExceptionEventResponse toResponse(ExceptionEvent e, Employee emp) {
        return new ExceptionDtos.ExceptionEventResponse(
                e.getId(),
                e.getEmployeeId(),
                emp == null ? null : emp.getFirstName() + " " + emp.getLastName(),
                e.getDailyTimeCardId(),
                e.getWorkDate(),
                e.getExceptionType(),
                e.getSeverity(),
                e.getDetailsJson(),
                e.getStatus(),
                e.getResolvedBy(),
                e.getResolvedAt(),
                e.getResolutionNote(),
                e.getVersion());
    }

    public record EmittedException(ExceptionType type, ExceptionSeverity severity, String detailsJson) {
        public EmittedException(ExceptionType type, ExceptionSeverity severity) {
            this(type, severity, null);
        }
    }
}

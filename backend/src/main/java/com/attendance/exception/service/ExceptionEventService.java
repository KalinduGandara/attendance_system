package com.attendance.exception.service;

import com.attendance.exception.domain.ExceptionEvent;
import com.attendance.exception.domain.ExceptionSeverity;
import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.domain.ExceptionType;
import com.attendance.exception.repository.ExceptionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    public ExceptionEventService(ExceptionEventRepository repository) {
        this.repository = repository;
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

    public record EmittedException(ExceptionType type, ExceptionSeverity severity, String detailsJson) {
        public EmittedException(ExceptionType type, ExceptionSeverity severity) {
            this(type, severity, null);
        }
    }
}

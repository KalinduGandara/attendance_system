package com.attendance.timecard.service;

import com.attendance.organization.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Listens for {@link PunchEventIngestedEvent} published after a successful
 * ingest and triggers a recompute of the affected (employee, work_date).
 *
 * <p>Uses {@code AFTER_COMMIT} so the punch row is guaranteed to be visible
 * when the recompute runs.
 */
@Component
public class TimeCardRecomputeListener {

    private static final Logger log = LoggerFactory.getLogger(TimeCardRecomputeListener.class);

    private final TimeCardRecomputeService recomputeService;
    private final EmployeeService employeeService;

    public TimeCardRecomputeListener(TimeCardRecomputeService recomputeService,
                                     EmployeeService employeeService) {
        this.recomputeService = recomputeService;
        this.employeeService = employeeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPunchIngested(PunchEventIngestedEvent event) {
        try {
            ZoneId zone = ZoneId.of(employeeService.timezoneForEmployee(event.employeeId()));
            var workDate = ZonedDateTime.ofInstant(event.eventTimeUtc(), zone).toLocalDate();
            recomputeService.recompute(event.employeeId(), workDate);
        } catch (RuntimeException ex) {
            log.error("Recompute failed for employee={} eventTime={}",
                    event.employeeId(), event.eventTimeUtc(), ex);
        }
    }
}

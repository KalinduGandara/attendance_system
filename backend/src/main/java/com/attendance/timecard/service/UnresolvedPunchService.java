package com.attendance.timecard.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.repository.PunchEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reconciliation workflow for {@code UNRESOLVED} punches: an operator assigns
 * the punch to a specific employee. The punch is flipped to {@code PROCESSED}
 * and a {@link PunchEventIngestedEvent} fires so the affected time card is
 * recomputed via the same listener path as a fresh ingest.
 */
@Service
public class UnresolvedPunchService {

    private final PunchEventRepository punchRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UnresolvedPunchService(PunchEventRepository punchRepository,
                                  EmployeeRepository employeeRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.punchRepository = punchRepository;
        this.employeeRepository = employeeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PunchEvent assignToEmployee(UUID punchId, UUID employeeId) {
        PunchEvent p = punchRepository.findById(punchId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Punch event not found"));
        if (p.getStatus() != PunchEventStatus.UNRESOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Only UNRESOLVED punches can be assigned");
        }
        if (employeeRepository.findById(employeeId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "Unknown employee");
        }
        p.setEmployeeId(employeeId);
        p.setStatus(PunchEventStatus.PROCESSED);
        p.setProcessedAt(Instant.now());
        PunchEvent saved = punchRepository.saveAndFlush(p);
        eventPublisher.publishEvent(new PunchEventIngestedEvent(
                saved.getId(), saved.getEmployeeId(), saved.getEventTimeUtc()));
        return saved;
    }
}

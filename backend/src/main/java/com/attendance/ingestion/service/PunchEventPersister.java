package com.attendance.ingestion.service;

import com.attendance.device.domain.CredentialType;
import com.attendance.device.service.CredentialResolutionService;
import com.attendance.ingestion.domain.IngestionStatus;
import com.attendance.ingestion.web.PunchIngestionDtos.EventResult;
import com.attendance.platform.security.TokenHasher;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.service.PunchEventIngestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-event persistence. Each call runs in its own transaction (REQUIRES_NEW)
 * so a failure on one event does not poison the rest of the batch and so each
 * accepted event triggers its own {@link PunchEventIngestedEvent} after commit.
 *
 * <p>Pre-checks the idempotency tuple before insert so the happy path never
 * relies on catching a {@code DataIntegrityViolationException}.
 */
@Service
public class PunchEventPersister {

    private final PunchEventRepository repository;
    private final CredentialResolutionService credentialResolutionService;
    private final ApplicationEventPublisher eventPublisher;

    public PunchEventPersister(PunchEventRepository repository,
                               CredentialResolutionService credentialResolutionService,
                               ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.credentialResolutionService = credentialResolutionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventResult persist(UUID sourceId,
                               String externalEventId,
                               PunchEventType eventType,
                               Instant eventTimeUtc,
                               CredentialType credentialType,
                               String credentialValue,
                               UUID explicitEmployeeId,
                               UUID deviceId,
                               String rawPayloadJson) {
        Optional<PunchEvent> existing = repository
                .findByIngestionSourceIdAndExternalEventId(sourceId, externalEventId);
        if (existing.isPresent()) {
            return new EventResult(externalEventId, IngestionStatus.DUPLICATE.name(),
                    existing.get().getId(), null);
        }

        UUID employeeId = null;
        String credentialHash = null;
        if (credentialValue != null && !credentialValue.isBlank()) {
            credentialHash = TokenHasher.sha256(credentialValue);
            employeeId = credentialResolutionService
                    .resolveEmployeeId(credentialType, credentialValue)
                    .orElse(null);
        } else if (explicitEmployeeId != null) {
            employeeId = explicitEmployeeId;
        }

        PunchEvent entity = new PunchEvent();
        entity.setIngestionSourceId(sourceId);
        entity.setExternalEventId(externalEventId);
        entity.setEventType(eventType);
        entity.setEventTimeUtc(eventTimeUtc);
        entity.setDeviceId(deviceId);
        entity.setCredentialValueHash(credentialHash);
        entity.setRawPayloadJson(rawPayloadJson);
        entity.setEmployeeId(employeeId);
        if (employeeId == null) {
            entity.setStatus(PunchEventStatus.UNRESOLVED);
        } else {
            entity.setStatus(PunchEventStatus.PROCESSED);
            entity.setProcessedAt(Instant.now());
        }

        PunchEvent saved = repository.saveAndFlush(entity);
        if (saved.getStatus() == PunchEventStatus.PROCESSED) {
            eventPublisher.publishEvent(new PunchEventIngestedEvent(
                    saved.getId(), saved.getEmployeeId(), saved.getEventTimeUtc()));
            return new EventResult(externalEventId, IngestionStatus.ACCEPTED.name(), saved.getId(), null);
        }
        return new EventResult(externalEventId, IngestionStatus.UNRESOLVED_CREDENTIAL.name(),
                saved.getId(), "No matching active credential");
    }
}

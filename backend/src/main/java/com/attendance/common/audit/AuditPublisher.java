package com.attendance.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AuditPublisher(AuditEventRepository repository,
                          ObjectMapper objectMapper,
                          ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    void wireListener() {
        AuditEntityListener.init(this, objectMapper);
    }

    /**
     * Called by {@link AuditEntityListener} during JPA {@code @PostPersist}/{@code @PostUpdate}/
     * {@code @PostRemove} callbacks. Publishes an internal Spring event so the audit row is
     * written either after the current transaction commits (when one is active) or immediately
     * (otherwise).
     */
    public void publish(AuditEvent event) {
        eventPublisher.publishEvent(new AuditEventReady(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEventReady(AuditEventReady wrapper) {
        try {
            log.debug("Persisting audit event: action={} entityType={} entityId={}",
                    wrapper.event().getAction(), wrapper.event().getEntityType(),
                    wrapper.event().getEntityId());
            repository.save(wrapper.event());
        } catch (RuntimeException ex) {
            log.warn("Failed to persist audit event", ex);
        }
    }

    public record AuditEventReady(AuditEvent event) {
    }
}

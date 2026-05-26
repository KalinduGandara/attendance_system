package com.attendance.common.audit;

import com.attendance.common.jpa.BaseEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA listener that records audit rows for entities annotated with {@link Auditable}.
 *
 * <p>The listener uses {@link AuditPublisher} (set once during application startup) to
 * decouple JPA lifecycle callbacks from Spring's container — JPA instantiates listeners
 * directly, so they cannot use injected fields.
 */
public class AuditEntityListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEntityListener.class);
    private static AuditPublisher publisher;
    private static ObjectMapper objectMapper;

    public static void init(AuditPublisher p, ObjectMapper om) {
        publisher = p;
        objectMapper = om;
    }

    @PostPersist
    public void onCreate(Object entity) {
        record(entity, "CREATE");
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        record(entity, "UPDATE");
    }

    @PostRemove
    public void onDelete(Object entity) {
        record(entity, "DELETE");
    }

    private void record(Object entity, String action) {
        if (publisher == null) {
            return;
        }
        try {
            AuditEvent event = new AuditEvent();
            event.setAction(action);
            event.setEntityType(resolveEntityType(entity));
            event.setEntityId(resolveEntityId(entity));
            event.setActorUserId(RequestContext.actorUserId().orElse(null));
            event.setActorUsername(RequestContext.actorUsername());
            event.setIp(RequestContext.clientIp());
            event.setUserAgent(RequestContext.userAgent());
            event.setRequestId(RequestContext.requestId());
            event.setOccurredAt(Instant.now());
            event.setAfterJson(toJson(entity));
            publisher.publish(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to record audit event for {}", entity.getClass().getSimpleName(), ex);
        }
    }

    private String resolveEntityType(Object entity) {
        Auditable auditable = entity.getClass().getAnnotation(Auditable.class);
        if (auditable != null && !auditable.value().isBlank()) {
            return auditable.value();
        }
        return entity.getClass().getSimpleName();
    }

    private UUID resolveEntityId(Object entity) {
        if (entity instanceof BaseEntity be) {
            return be.getId();
        }
        return null;
    }

    private String toJson(Object entity) {
        if (objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

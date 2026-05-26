package com.attendance.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditPublisher {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditPublisher(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void wireListener() {
        AuditEntityListener.init(this, objectMapper);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(AuditEvent event) {
        repository.save(event);
    }
}

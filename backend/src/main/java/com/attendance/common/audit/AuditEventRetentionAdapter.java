package com.attendance.common.audit;

import com.attendance.common.retention.BatchPurger;
import com.attendance.common.retention.RetentionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Retention purge for the append-only {@code audit_event} table. */
@Component
public class AuditEventRetentionAdapter implements RetentionPort {

    private final AuditEventRepository repository;

    public AuditEventRetentionAdapter(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public String entityType() {
        return "audit_event";
    }

    @Override
    @Transactional
    public long purgeOlderThan(Instant cutoff, int batchSize, int maxBatches) {
        return BatchPurger.purge(batchSize, maxBatches,
                page -> repository.findIdsOlderThan(cutoff, page),
                repository::deleteAllByIdInBatch);
    }
}

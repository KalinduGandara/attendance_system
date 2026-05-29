package com.attendance.timecard.service;

import com.attendance.common.retention.BatchPurger;
import com.attendance.common.retention.RetentionPort;
import com.attendance.timecard.repository.PunchEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Retention purge for raw {@code punch_event} rows (SRS NFR-3: automatic
 * deletion of old punch logs). Punches referenced by a manual edit are kept.
 */
@Component
public class PunchEventRetentionAdapter implements RetentionPort {

    private final PunchEventRepository repository;

    public PunchEventRetentionAdapter(PunchEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public String entityType() {
        return "punch_event";
    }

    @Override
    @Transactional
    public long purgeOlderThan(Instant cutoff, int batchSize, int maxBatches) {
        return BatchPurger.purge(batchSize, maxBatches,
                page -> repository.findIdsOlderThan(cutoff, page),
                repository::deleteAllByIdInBatch);
    }
}

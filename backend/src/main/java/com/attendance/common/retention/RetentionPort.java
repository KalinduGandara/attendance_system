package com.attendance.common.retention;

import java.time.Instant;

/**
 * A purge capability for one retention-managed entity type. Each feature module
 * that owns purgeable data contributes one bean; the admin module's retention
 * job discovers them by {@link #entityType()} and never reaches across module
 * boundaries into another module's repository.
 */
public interface RetentionPort {

    /** Matches {@code retention_policy.entity_type} (e.g. {@code "punch_event"}). */
    String entityType();

    /**
     * Deletes rows strictly older than {@code cutoff}, in batches of
     * {@code batchSize}, up to {@code maxBatches} batches per invocation.
     *
     * @return the number of rows deleted
     */
    long purgeOlderThan(Instant cutoff, int batchSize, int maxBatches);
}

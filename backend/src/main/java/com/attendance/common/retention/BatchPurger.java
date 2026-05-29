package com.attendance.common.retention;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared bounded-batch delete loop used by {@link RetentionPort} adapters.
 * Repeatedly fetches a page of the oldest matching ids and deletes them, so a
 * purge never builds one unbounded transaction over millions of rows.
 */
public final class BatchPurger {

    private BatchPurger() {
    }

    /**
     * @param batchSize    rows per batch (clamped to ≥ 1)
     * @param maxBatches   safety ceiling on batches per call (clamped to ≥ 1)
     * @param findOldestIds given a {@link Pageable} for the first page of size N,
     *                      returns up to N ids of the oldest eligible rows
     * @param deleteByIds  deletes exactly the supplied ids
     * @return total rows deleted
     */
    public static long purge(int batchSize,
                             int maxBatches,
                             Function<Pageable, List<UUID>> findOldestIds,
                             Consumer<List<UUID>> deleteByIds) {
        int size = Math.max(1, batchSize);
        int batches = Math.max(1, maxBatches);
        Pageable page = PageRequest.of(0, size);
        long total = 0;
        for (int i = 0; i < batches; i++) {
            List<UUID> ids = findOldestIds.apply(page);
            if (ids.isEmpty()) {
                break;
            }
            deleteByIds.accept(ids);
            total += ids.size();
            if (ids.size() < size) {
                break;
            }
        }
        return total;
    }
}

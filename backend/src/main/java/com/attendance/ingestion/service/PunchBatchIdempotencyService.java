package com.attendance.ingestion.service;

import com.attendance.ingestion.web.PunchIngestionDtos;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches the response for a previously-seen {@code Idempotency-Key} so a
 * retried batch returns the exact same response without re-applying any
 * side effects.
 *
 * <p>30-minute TTL covers reasonable client retry windows; we deliberately do
 * not persist this — DB-level uniqueness on
 * {@code (ingestion_source_id, external_event_id)} makes us correct (not just
 * fast) even on cache miss.
 */
@Service
public class PunchBatchIdempotencyService {

    private final Cache<String, PunchIngestionDtos.IngestionResponse> cache;

    public PunchBatchIdempotencyService() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    public Optional<PunchIngestionDtos.IngestionResponse> lookup(UUID sourceId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(key(sourceId, idempotencyKey)));
    }

    public void store(UUID sourceId, String idempotencyKey,
                      PunchIngestionDtos.IngestionResponse response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        cache.put(key(sourceId, idempotencyKey), response);
    }

    private static String key(UUID sourceId, String idempotencyKey) {
        return sourceId + ":" + idempotencyKey;
    }
}

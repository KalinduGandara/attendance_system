package com.attendance.admin.service;

import com.attendance.admin.domain.RetentionPolicy;
import com.attendance.admin.repository.RetentionPolicyRepository;
import com.attendance.common.error.ApiException;
import com.attendance.common.retention.RetentionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages per-entity retention policies and runs the purge. Each enabled policy
 * deletes rows older than its window via the owning module's {@link RetentionPort}
 * — the admin module never touches another module's repository directly.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 1000;

    private final RetentionPolicyRepository policyRepository;
    private final SystemSettingService settings;
    private final ObjectProvider<RetentionService> self;
    private final Map<String, RetentionPort> ports;

    public RetentionService(RetentionPolicyRepository policyRepository,
                            SystemSettingService settings,
                            ObjectProvider<RetentionService> self,
                            List<RetentionPort> retentionPorts) {
        this.policyRepository = policyRepository;
        this.settings = settings;
        this.self = self;
        this.ports = retentionPorts.stream()
                .collect(Collectors.toMap(RetentionPort::entityType, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<RetentionPolicy> getPolicies() {
        return policyRepository.findAllByOrderByEntityTypeAsc();
    }

    @Transactional
    public RetentionPolicy updatePolicy(String entityType, int retainDays, boolean enabled) {
        if (retainDays < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "retainDays must be at least 1");
        }
        if (!ports.containsKey(entityType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "No retention support for entity type: " + entityType);
        }
        RetentionPolicy policy = policyRepository.findByEntityType(entityType)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Retention policy not found: " + entityType));
        policy.setRetainDays(retainDays);
        policy.setEnabled(enabled);
        return policy;
    }

    /** Scheduler entry point: purges every enabled policy when retention is on. */
    public void runScheduledIfEnabled() {
        if (!settings.getBoolean("retention_enabled", false)) {
            log.debug("Scheduled retention purge skipped: retention_enabled=false");
            return;
        }
        purgeAllEnabled();
    }

    /**
     * Purges every enabled policy. Returns entity_type → rows deleted. Not
     * transactional itself: each {@link #purge} runs in its own transaction
     * (via the proxy), so one entity failing neither rolls back nor halts the
     * others.
     */
    public Map<String, Long> purgeAllEnabled() {
        Map<String, Long> results = new LinkedHashMap<>();
        for (RetentionPolicy policy : policyRepository.findByEnabledTrue()) {
            String type = policy.getEntityType();
            try {
                results.put(type, self.getObject().purge(type));
            } catch (RuntimeException ex) {
                log.warn("Retention purge for {} failed", type, ex);
                results.put(type, -1L);
            }
        }
        return results;
    }

    /**
     * Purges a single entity type per its policy and records the result. The
     * policy update runs in its own transaction so a partial failure of one
     * entity does not roll back another's recorded result.
     */
    @Transactional
    public long purge(String entityType) {
        RetentionPolicy policy = policyRepository.findByEntityType(entityType)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Retention policy not found: " + entityType));
        RetentionPort port = ports.get(entityType);
        if (port == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "No retention support for entity type: " + entityType);
        }
        Instant cutoff = Instant.now().minus(policy.getRetainDays(), ChronoUnit.DAYS);
        long deleted = port.purgeOlderThan(cutoff, BATCH_SIZE, MAX_BATCHES_PER_RUN);
        policy.setLastRunAt(Instant.now());
        policy.setLastRunDeleted(deleted);
        log.info("Retention purge for {} deleted {} rows older than {}", entityType, deleted, cutoff);
        return deleted;
    }
}

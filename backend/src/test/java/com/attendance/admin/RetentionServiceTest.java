package com.attendance.admin;

import com.attendance.admin.domain.RetentionPolicy;
import com.attendance.admin.repository.RetentionPolicyRepository;
import com.attendance.admin.service.RetentionService;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.repository.PunchEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the retention purge respects the configured day count and never
 * touches data inside the window (plan.md §9 acceptance). Exercises the real
 * admin → {@code RetentionPort} (timecard) path against {@code punch_event},
 * the SRS NFR-3 target.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RetentionServiceTest {

    @Autowired RetentionService retentionService;
    @Autowired RetentionPolicyRepository policyRepository;
    @Autowired PunchEventRepository punchEventRepository;
    @PersistenceContext EntityManager em;

    private UUID oldId;
    private UUID recentId;

    @BeforeEach
    void seed() {
        punchEventRepository.deleteAll();
        policyRepository.deleteAll();

        RetentionPolicy policy = new RetentionPolicy();
        policy.setEntityType("punch_event");
        policy.setRetainDays(30);
        policy.setEnabled(true);
        policyRepository.save(policy);

        oldId = savePunch("old", Instant.now().minus(60, ChronoUnit.DAYS));
        recentId = savePunch("recent", Instant.now().minus(1, ChronoUnit.DAYS));
        em.flush();
    }

    private UUID savePunch(String externalId, Instant when) {
        PunchEvent p = new PunchEvent();
        p.setIngestionSourceId(UUID.randomUUID());
        p.setExternalEventId(externalId);
        p.setEventType(PunchEventType.CHECK_IN);
        p.setEventTimeUtc(when);
        p.setStatus(PunchEventStatus.PROCESSED);
        return punchEventRepository.save(p).getId();
    }

    @Test
    void purge_deletes_rows_older_than_window_and_keeps_recent() {
        long deleted = retentionService.purge("punch_event");
        em.flush();
        em.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(punchEventRepository.findById(oldId)).isEmpty();
        assertThat(punchEventRepository.findById(recentId)).isPresent();

        RetentionPolicy policy = policyRepository.findByEntityType("punch_event").orElseThrow();
        assertThat(policy.getLastRunDeleted()).isEqualTo(1);
        assertThat(policy.getLastRunAt()).isNotNull();
    }

    @Test
    void purgeAllEnabled_runs_only_enabled_policies() {
        var results = retentionService.purgeAllEnabled();
        em.flush();
        em.clear();

        assertThat(results).containsOnlyKeys("punch_event");
        assertThat(results.get("punch_event")).isEqualTo(1L);
        assertThat(punchEventRepository.findById(recentId)).isPresent();
    }
}

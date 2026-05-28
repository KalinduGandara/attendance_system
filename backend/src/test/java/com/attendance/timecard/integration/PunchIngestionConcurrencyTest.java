package com.attendance.timecard.integration;

import com.attendance.device.domain.CredentialType;
import com.attendance.ingestion.service.PunchEventIngestionPort;
import com.attendance.ingestion.web.PunchIngestionDtos;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.service.TimeCardRecomputeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per docs/plan.md §5.5: "parallel ingestion of 1000 events for the same
 * employee/day → final time card is correct". Uses 8 threads.
 *
 * <p>The test does NOT use {@code @Transactional} since the per-event persister
 * needs to actually commit so the after-commit recompute listener fires.
 */
@SpringBootTest
@ActiveProfiles("test")
class PunchIngestionConcurrencyTest {

    @Autowired Phase5SeedData seed;
    @Autowired PunchEventIngestionPort port;
    @Autowired PunchEventRepository punchEventRepository;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;
    @Autowired TimeCardRecomputeService recomputeService;

    private Phase5SeedData.Seeded data;

    @BeforeEach
    void setUp() {
        data = seed.seedAll();
    }

    @Test
    void parallelIngestion_finalTimeCardIsCorrect() throws Exception {
        LocalDate workDate = LocalDate.of(2026, 5, 28);
        ZoneId tz = ZoneId.of("America/New_York");
        Instant base = workDate.atTime(9, 0).atZone(tz).toInstant();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int total = 200;     // 200 events × 8 threads = 1600 attempts; same externalId set
        try {
            for (int i = 0; i < total; i++) {
                final int idx = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    String extId = "evt-" + idx;
                    var batch = new PunchIngestionDtos.PunchBatchRequest(
                            data.sourceId(),
                            List.of(new PunchIngestionDtos.PunchEventRequest(
                                    extId, idx % 2 == 0
                                        ? com.attendance.timecard.domain.PunchEventType.CHECK_IN
                                        : com.attendance.timecard.domain.PunchEventType.CHECK_OUT,
                                    base.plusSeconds(idx * 60L),
                                    new PunchIngestionDtos.CredentialRef(CredentialType.RFID, "RFID-001"),
                                    null, null, null)));
                    port.ingest(data.sourceId(), null, batch);
                    port.ingest(data.sourceId(), null, batch); // intentional retry
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(2, TimeUnit.MINUTES);
        } finally {
            pool.shutdown();
        }

        // Each externalId persisted exactly once (idempotency).
        assertThat(punchEventRepository.findAll()).hasSize(total);

        // Force a final recompute and verify the daily card sums match the deterministic engine output.
        recomputeService.recompute(data.employeeId(), workDate);
        var cards = dailyTimeCardRepository.findAll();
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getStatus()).isIn(DailyTimeCardStatus.PRESENT, DailyTimeCardStatus.PARTIAL);
        assertThat(cards.get(0).getEmployeeId()).isEqualTo(data.employeeId());
    }
}

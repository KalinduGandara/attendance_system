package com.attendance.timecard.integration;

import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.service.NightlyRecomputeJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NightlyRecomputeJobTest {

    @Autowired Phase5SeedData seed;
    @Autowired NightlyRecomputeJob job;
    @Autowired DailyTimeCardRepository repository;

    @BeforeEach
    void setUp() {
        seed.seedAll();
    }

    @Test
    void runForDateRecomputesAllActiveEmployees() {
        LocalDate workDate = LocalDate.of(2026, 5, 28);
        int recomputed = job.runForDate(workDate);

        assertThat(recomputed).isEqualTo(1);
        var cards = repository.findAll();
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getStatus()).isEqualTo(DailyTimeCardStatus.ABSENT);
    }
}

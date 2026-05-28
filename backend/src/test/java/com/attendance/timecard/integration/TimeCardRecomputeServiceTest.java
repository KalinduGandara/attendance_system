package com.attendance.timecard.integration;

import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.service.TimeCardRecomputeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimeCardRecomputeServiceTest {

    @Autowired Phase5SeedData seed;
    @Autowired TimeCardRecomputeService recomputeService;
    @Autowired PunchEventRepository punchEventRepository;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;

    private Phase5SeedData.Seeded data;

    @BeforeEach
    void setUp() {
        data = seed.seedAll();
    }

    @Test
    void recomputeProducesPresentTimeCard() {
        LocalDate workDate = LocalDate.of(2026, 5, 28);  // Thursday
        savePunch(data.employeeId(), "evt-in", PunchEventType.CHECK_IN,
                workDate.atTime(9, 0).atZone(java.time.ZoneId.of("America/New_York")).toInstant());
        savePunch(data.employeeId(), "evt-out", PunchEventType.CHECK_OUT,
                workDate.atTime(17, 0).atZone(java.time.ZoneId.of("America/New_York")).toInstant());

        DailyTimeCard card = recomputeService.recompute(data.employeeId(), workDate);

        assertThat(card.getStatus()).isEqualTo(DailyTimeCardStatus.PRESENT);
        assertThat(card.getWorkedMinutes()).isEqualTo(480);
        assertThat(card.getBreakdowns()).hasSize(1);
    }

    @Test
    void recomputeTwiceIsDeterministic() {
        LocalDate workDate = LocalDate.of(2026, 5, 28);
        savePunch(data.employeeId(), "evt-in", PunchEventType.CHECK_IN,
                workDate.atTime(9, 0).atZone(java.time.ZoneId.of("America/New_York")).toInstant());
        savePunch(data.employeeId(), "evt-out", PunchEventType.CHECK_OUT,
                workDate.atTime(17, 0).atZone(java.time.ZoneId.of("America/New_York")).toInstant());

        DailyTimeCard a = recomputeService.recompute(data.employeeId(), workDate);
        int workedA = a.getWorkedMinutes();
        UUID idA = a.getId();

        DailyTimeCard b = recomputeService.recompute(data.employeeId(), workDate);
        assertThat(b.getId()).isEqualTo(idA);   // upsert, not insert
        assertThat(b.getWorkedMinutes()).isEqualTo(workedA);
        assertThat(b.getStatus()).isEqualTo(a.getStatus());
    }

    @Test
    void recomputeWithoutPunchesProducesAbsent() {
        LocalDate workDate = LocalDate.of(2026, 5, 28);
        DailyTimeCard card = recomputeService.recompute(data.employeeId(), workDate);
        assertThat(card.getStatus()).isEqualTo(DailyTimeCardStatus.ABSENT);
    }

    @Test
    void recomputeOnWeekendProducesOff() {
        LocalDate workDate = LocalDate.of(2026, 5, 30);  // Saturday — template says off
        DailyTimeCard card = recomputeService.recompute(data.employeeId(), workDate);
        assertThat(card.getStatus()).isEqualTo(DailyTimeCardStatus.OFF);
    }

    private void savePunch(UUID employeeId, String externalId, PunchEventType type, Instant at) {
        PunchEvent e = new PunchEvent();
        e.setIngestionSourceId(data.sourceId());
        e.setExternalEventId(externalId);
        e.setEventType(type);
        e.setEventTimeUtc(at);
        e.setEmployeeId(employeeId);
        e.setStatus(PunchEventStatus.PROCESSED);
        e.setProcessedAt(Instant.now());
        punchEventRepository.saveAndFlush(e);
    }
}

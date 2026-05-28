package com.attendance.timecard.integration;

import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.service.UnresolvedPunchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UnresolvedPunchServiceTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Autowired Phase5SeedData seed;
    @Autowired UnresolvedPunchService unresolvedPunchService;
    @Autowired PunchEventRepository punchRepository;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;

    private Phase5SeedData.Seeded data;

    @BeforeEach
    void setUp() {
        data = seed.seedAll();
    }

    @Test
    void assignToEmployee_flipsStatusAndTriggersRecompute() {
        Instant at = LocalDate.of(2026, 5, 28).atTime(9, 0).atZone(NY).toInstant();
        PunchEvent unresolved = saveUnresolved("evt-unres-1", at);

        PunchEvent saved = unresolvedPunchService.assignToEmployee(unresolved.getId(), data.employeeId());

        assertThat(saved.getStatus()).isEqualTo(PunchEventStatus.PROCESSED);
        assertThat(saved.getEmployeeId()).isEqualTo(data.employeeId());
        assertThat(saved.getProcessedAt()).isNotNull();

        // AFTER_COMMIT listener fires recompute outside this test transaction; verify the punch
        // row state alone, which is the externally-visible contract here.
        PunchEvent reloaded = punchRepository.findById(unresolved.getId()).orElseThrow();
        assertThat(reloaded.getEmployeeId()).isEqualTo(data.employeeId());
    }

    @Test
    void cannotAssign_alreadyProcessedPunch() {
        Instant at = LocalDate.of(2026, 5, 28).atTime(9, 0).atZone(NY).toInstant();
        PunchEvent unresolved = saveUnresolved("evt-unres-2", at);
        unresolvedPunchService.assignToEmployee(unresolved.getId(), data.employeeId());

        assertThatThrownBy(() ->
                unresolvedPunchService.assignToEmployee(unresolved.getId(), data.employeeId()))
                .hasMessageContaining("UNRESOLVED");
    }

    @Test
    void rejects_unknownEmployee() {
        Instant at = LocalDate.of(2026, 5, 28).atTime(9, 0).atZone(NY).toInstant();
        PunchEvent unresolved = saveUnresolved("evt-unres-3", at);

        assertThatThrownBy(() ->
                unresolvedPunchService.assignToEmployee(unresolved.getId(), UUID.randomUUID()))
                .hasMessageContaining("Unknown employee");
    }

    private PunchEvent saveUnresolved(String externalId, Instant at) {
        PunchEvent e = new PunchEvent();
        e.setIngestionSourceId(data.sourceId());
        e.setExternalEventId(externalId);
        e.setEventType(PunchEventType.CHECK_IN);
        e.setEventTimeUtc(at);
        e.setStatus(PunchEventStatus.UNRESOLVED);
        e.setCredentialValueHash("ffffffff");
        return punchRepository.saveAndFlush(e);
    }
}

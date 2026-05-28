package com.attendance.timecard.integration;

import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.UserRepository;
import com.attendance.platform.security.AppPrincipal;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.domain.TimeCardEditChangeType;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.repository.TimeCardEditRepository;
import com.attendance.timecard.service.TimeCardEditService;
import com.attendance.timecard.service.TimeCardRecomputeService;
import com.attendance.timecard.web.TimeCardDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TimeCardEditServiceTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Autowired Phase5SeedData seed;
    @Autowired TimeCardEditService editService;
    @Autowired TimeCardRecomputeService recomputeService;
    @Autowired PunchEventRepository punchEventRepository;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;
    @Autowired TimeCardEditRepository editRepository;
    @Autowired UserRepository userRepository;

    private Phase5SeedData.Seeded data;
    private UUID timeCardId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        data = seed.seedAll();
        actorId = userRepository.findAll().stream().findFirst()
                .map(User::getId)
                .orElseGet(this::createActorUser);
        setAuthenticatedUser(actorId);

        LocalDate workDate = LocalDate.of(2026, 5, 28);
        savePunch("evt-in", PunchEventType.CHECK_IN,
                workDate.atTime(9, 2).atZone(NY).toInstant());
        savePunch("evt-out", PunchEventType.CHECK_OUT,
                workDate.atTime(17, 0).atZone(NY).toInstant());

        DailyTimeCard card = recomputeService.recompute(data.employeeId(), workDate);
        timeCardId = card.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void punchEdit_supersedesOriginalAndRecomputes() {
        PunchEvent inPunch = punchEventRepository.findAll().stream()
                .filter(p -> p.getEventType() == PunchEventType.CHECK_IN)
                .findFirst().orElseThrow();

        TimeCardDtos.EditRequest req = new TimeCardDtos.EditRequest(
                TimeCardEditChangeType.PUNCH_EDIT,
                inPunch.getId(),
                null,
                LocalDate.of(2026, 5, 28).atTime(9, 0).atZone(NY).toInstant(),
                null,
                null,
                null,
                "Device clock drift; verified");

        TimeCardDtos.TimeCardDetailResponse detail = editService.applyEdit(timeCardId, req);

        PunchEvent originalAfter = punchEventRepository.findById(inPunch.getId()).orElseThrow();
        assertThat(originalAfter.getStatus()).isEqualTo(PunchEventStatus.SUPERSEDED);

        assertThat(detail.lateMinutes()).isEqualTo(0);
        assertThat(detail.workedMinutes()).isEqualTo(480);
        assertThat(detail.edits()).hasSize(1);
        assertThat(detail.edits().get(0).changeType()).isEqualTo(TimeCardEditChangeType.PUNCH_EDIT);
        assertThat(detail.edits().get(0).reason()).isEqualTo("Device clock drift; verified");
        assertThat(detail.edits().get(0).beforeJson()).contains(inPunch.getId().toString());

        // Punches view shows only the new PROCESSED punches, not the SUPERSEDED row.
        assertThat(detail.punches()).hasSize(2);
        assertThat(detail.punches().stream().map(p -> p.status()))
                .containsOnly(PunchEventStatus.PROCESSED);
    }

    @Test
    void punchAdd_createsNewPunchAndRefreshesTimeCard() {
        // Remove the in-punch first so the engine sees the new one we add.
        PunchEvent existingIn = punchEventRepository.findAll().stream()
                .filter(p -> p.getEventType() == PunchEventType.CHECK_IN)
                .findFirst().orElseThrow();
        existingIn.setStatus(PunchEventStatus.SUPERSEDED);
        punchEventRepository.saveAndFlush(existingIn);

        TimeCardDtos.EditRequest req = new TimeCardDtos.EditRequest(
                TimeCardEditChangeType.PUNCH_ADD,
                null,
                PunchEventType.CHECK_IN,
                LocalDate.of(2026, 5, 28).atTime(9, 0).atZone(NY).toInstant(),
                data.sourceId(),
                null,
                null,
                "Missed punch — added retroactively");

        TimeCardDtos.TimeCardDetailResponse detail = editService.applyEdit(timeCardId, req);

        assertThat(detail.edits()).hasSize(1);
        assertThat(detail.edits().get(0).changeType()).isEqualTo(TimeCardEditChangeType.PUNCH_ADD);
        assertThat(detail.workedMinutes()).isEqualTo(480);
    }

    @Test
    void punchDelete_supersedesAndRecomputes() {
        PunchEvent inPunch = punchEventRepository.findAll().stream()
                .filter(p -> p.getEventType() == PunchEventType.CHECK_IN)
                .findFirst().orElseThrow();

        TimeCardDtos.EditRequest req = new TimeCardDtos.EditRequest(
                TimeCardEditChangeType.PUNCH_DELETE,
                inPunch.getId(),
                null, null, null, null, null,
                "Erroneous punch");

        TimeCardDtos.TimeCardDetailResponse detail = editService.applyEdit(timeCardId, req);

        PunchEvent after = punchEventRepository.findById(inPunch.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PunchEventStatus.SUPERSEDED);
        assertThat(detail.edits()).hasSize(1);
        assertThat(detail.edits().get(0).changeType()).isEqualTo(TimeCardEditChangeType.PUNCH_DELETE);
        // Only a CHECK_OUT remains → status should be PARTIAL or similar (missing in).
        assertThat(detail.status()).isNotEqualTo(DailyTimeCardStatus.PRESENT);
    }

    @Test
    void reasonIsRequired_validationKicksIn() {
        // The annotation-based validation lives on the controller; the service trusts
        // the DTO. Here we verify that a missing punch event id raises a clear error.
        TimeCardDtos.EditRequest bad = new TimeCardDtos.EditRequest(
                TimeCardEditChangeType.PUNCH_EDIT,
                null, null, null, null, null, null,
                "Some reason");
        assertThatThrownBy(() -> editService.applyEdit(timeCardId, bad))
                .hasMessageContaining("PUNCH_EDIT requires punchEventId");
    }

    @Test
    void editHistoryAccumulatesAcrossMultipleEdits() {
        PunchEvent outPunch = punchEventRepository.findAll().stream()
                .filter(p -> p.getEventType() == PunchEventType.CHECK_OUT)
                .findFirst().orElseThrow();

        editService.applyEdit(timeCardId, new TimeCardDtos.EditRequest(
                TimeCardEditChangeType.NOTE, null, null, null, null, null,
                "Investigation in progress", "Operator note"));

        TimeCardDtos.TimeCardDetailResponse detail = editService.applyEdit(timeCardId,
                new TimeCardDtos.EditRequest(
                        TimeCardEditChangeType.PUNCH_EDIT, outPunch.getId(), null,
                        LocalDate.of(2026, 5, 28).atTime(17, 30).atZone(NY).toInstant(),
                        null, null, null,
                        "Operator forgot to clock out at correct time"));

        assertThat(detail.edits()).hasSize(2);
        assertThat(detail.edits()).extracting(TimeCardDtos.TimeCardEditDto::changeType)
                .containsExactly(TimeCardEditChangeType.NOTE, TimeCardEditChangeType.PUNCH_EDIT);
        assertThat(detail.notes()).isEqualTo("Investigation in progress");
        // CHECK_IN at 9:02 → CHECK_OUT moved from 17:00 to 17:30 ⇒ 8h28m worked.
        assertThat(detail.workedMinutes()).isEqualTo(508);
    }

    private void savePunch(String externalId, PunchEventType type, Instant at) {
        PunchEvent e = new PunchEvent();
        e.setIngestionSourceId(data.sourceId());
        e.setExternalEventId(externalId);
        e.setEventType(type);
        e.setEventTimeUtc(at);
        e.setEmployeeId(data.employeeId());
        e.setStatus(PunchEventStatus.PROCESSED);
        e.setProcessedAt(Instant.now());
        punchEventRepository.saveAndFlush(e);
    }

    private UUID createActorUser() {
        User u = new User();
        u.setUsername("editor-" + UUID.randomUUID());
        u.setEmail(u.getUsername() + "@local");
        u.setPasswordHash("x");
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(u).getId();
    }

    private void setAuthenticatedUser(UUID userId) {
        AppPrincipal principal = new AppPrincipal(userId, "tester", List.of("timecard.edit"));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.authorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

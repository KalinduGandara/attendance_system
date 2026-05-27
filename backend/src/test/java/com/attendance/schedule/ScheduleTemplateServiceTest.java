package com.attendance.schedule;

import com.attendance.common.error.ApiException;
import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.domain.CycleType;
import com.attendance.schedule.repository.ScheduleAssignmentRepository;
import com.attendance.schedule.repository.ScheduleTemplateRepository;
import com.attendance.schedule.service.ScheduleTemplateService;
import com.attendance.schedule.web.ScheduleDtos;
import com.attendance.shift.domain.ShiftType;
import com.attendance.shift.repository.ShiftRepository;
import com.attendance.shift.service.ShiftService;
import com.attendance.shift.web.ShiftDtos;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduleTemplateServiceTest {

    @Autowired ScheduleTemplateService templateService;
    @Autowired ScheduleTemplateRepository templateRepository;
    @Autowired ScheduleAssignmentRepository assignmentRepository;
    @Autowired ShiftService shiftService;
    @Autowired ShiftRepository shiftRepository;
    @Autowired TimeCodeRepository timeCodeRepository;

    private UUID shiftAId;
    private UUID shiftBId;

    @BeforeEach
    void seed() {
        assignmentRepository.deleteAll();
        templateRepository.deleteAll();
        shiftRepository.deleteAll();
        timeCodeRepository.deleteAll();

        TimeCode reg = new TimeCode();
        reg.setCode("REG");
        reg.setName("Regular");
        reg.setCategory(TimeCodeCategory.ATTENDANCE);
        reg.setRate(new BigDecimal("1.00"));
        reg.setColor("#3b82f6");
        reg.setPaid(true);
        reg.setCountsForAttendance(true);
        reg.setActive(true);
        UUID regId = timeCodeRepository.saveAndFlush(reg).getId();

        shiftAId = shiftService.create(new ShiftDtos.ShiftRequest(
                "A", ShiftType.FIXED, "#3b82f6", null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(), null, null)).id();
        shiftBId = shiftService.create(new ShiftDtos.ShiftRequest(
                "B", ShiftType.FIXED, "#3b82f6", null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 1320, 1740, null)),
                List.of(), List.of(), List.of(), List.of(), null, null)).id();
    }

    @Test
    void create_weekly_template_with_days_round_trip() {
        var created = templateService.create(new ScheduleDtos.TemplateRequest(
                "Five-and-two", CycleType.WEEKLY, 7, "Mon-Fri Day",
                List.of(
                        new ScheduleDtos.TemplateDayRequest(0, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(1, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(2, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(3, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(4, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(5, null),
                        new ScheduleDtos.TemplateDayRequest(6, null)),
                null));
        assertThat(created.days()).hasSize(7);
        assertThat(created.days().stream()
                .filter(d -> d.shiftId() != null).count()).isEqualTo(5);
    }

    @Test
    void day_index_out_of_range_rejected() {
        assertThatThrownBy(() -> templateService.create(new ScheduleDtos.TemplateRequest(
                "Bad", CycleType.WEEKLY, 7, null,
                List.of(new ScheduleDtos.TemplateDayRequest(7, shiftAId)),
                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void duplicate_day_index_rejected() {
        assertThatThrownBy(() -> templateService.create(new ScheduleDtos.TemplateRequest(
                "Bad", CycleType.WEEKLY, 7, null,
                List.of(new ScheduleDtos.TemplateDayRequest(0, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(0, shiftBId)),
                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("duplicate day index");
    }

    @Test
    void duplicate_name_rejected() {
        templateService.create(new ScheduleDtos.TemplateRequest(
                "Std", CycleType.DAILY, 1, null, List.of(), null));
        assertThatThrownBy(() -> templateService.create(new ScheduleDtos.TemplateRequest(
                "Std", CycleType.DAILY, 1, null, List.of(), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_replaces_days_atomically() {
        // Two days, both pointing at shift A.
        var created = templateService.create(new ScheduleDtos.TemplateRequest(
                "Std", CycleType.WEEKLY, 7, null,
                List.of(new ScheduleDtos.TemplateDayRequest(0, shiftAId),
                        new ScheduleDtos.TemplateDayRequest(1, shiftAId)),
                null));

        // Update collapses to one day pointing at shift B — the old day rows
        // must be deleted (orphanRemoval), not left behind alongside the new
        // row.
        var updated = templateService.update(created.id(), new ScheduleDtos.TemplateRequest(
                "Std", CycleType.WEEKLY, 7, null,
                List.of(new ScheduleDtos.TemplateDayRequest(0, shiftBId)),
                null));

        assertThat(updated.days()).hasSize(1);
        assertThat(updated.days().get(0).shiftId()).isEqualTo(shiftBId);
    }

    @Test
    void update_with_stale_version_409() {
        var created = templateService.create(new ScheduleDtos.TemplateRequest(
                "Std", CycleType.DAILY, 1, null, List.of(), null));
        var renamed = templateService.update(created.id(), new ScheduleDtos.TemplateRequest(
                "Std2", CycleType.DAILY, 1, null, List.of(), null));
        assertThat(renamed.version()).isGreaterThan(created.version());

        assertThatThrownBy(() -> templateService.update(created.id(),
                new ScheduleDtos.TemplateRequest(
                        "Std3", CycleType.DAILY, 1, null, List.of(), created.version())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("modified by another request");
    }

    @Test
    void cannot_delete_template_referenced_by_assignment() {
        var tpl = templateService.create(new ScheduleDtos.TemplateRequest(
                "Std", CycleType.DAILY, 1, null, List.of(), null));

        // Persist a raw assignment row to avoid coupling to the org seed; the
        // service-level validators (employee/group existence) are exercised in
        // the assignment service test.
        var ent = new com.attendance.schedule.domain.ScheduleAssignment();
        ent.setTargetType(AssignmentTargetType.EMPLOYEE);
        ent.setTargetId(UUID.randomUUID());
        ent.setTemplateId(tpl.id());
        ent.setStartDate(LocalDate.of(2026, 6, 1));
        ent.setPriority(0);
        assignmentRepository.saveAndFlush(ent);

        assertThatThrownBy(() -> templateService.delete(tpl.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("referenced");
    }
}

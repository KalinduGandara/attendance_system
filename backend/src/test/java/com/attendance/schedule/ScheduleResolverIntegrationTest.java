package com.attendance.schedule;

import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.organization.service.UserGroupService;
import com.attendance.organization.web.EmployeeDtos;
import com.attendance.organization.web.UserGroupDtos;
import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.domain.CycleType;
import com.attendance.schedule.repository.ScheduleAssignmentRepository;
import com.attendance.schedule.repository.ScheduleTemplateRepository;
import com.attendance.schedule.repository.TemporaryScheduleRepository;
import com.attendance.schedule.service.ResolvedSchedule;
import com.attendance.schedule.service.ScheduleAssignmentService;
import com.attendance.schedule.service.ScheduleResolver;
import com.attendance.schedule.service.ScheduleTemplateService;
import com.attendance.schedule.service.TemporaryScheduleService;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduleResolverIntegrationTest {

    @Autowired ScheduleResolver resolver;
    @Autowired ScheduleTemplateService templateService;
    @Autowired ScheduleAssignmentService assignmentService;
    @Autowired TemporaryScheduleService temporaryService;
    @Autowired ShiftService shiftService;
    @Autowired EmployeeService employeeService;
    @Autowired UserGroupService userGroupService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserGroupRepository userGroupRepository;
    @Autowired ShiftRepository shiftRepository;
    @Autowired TimeCodeRepository timeCodeRepository;
    @Autowired ScheduleTemplateRepository scheduleTemplateRepository;
    @Autowired ScheduleAssignmentRepository scheduleAssignmentRepository;
    @Autowired TemporaryScheduleRepository temporaryScheduleRepository;

    private UUID regId;
    private UUID dayShiftId;
    private UUID nightShiftId;
    private UUID emergencyShiftId;
    private UUID employeeId;
    private UUID groupId;

    @BeforeEach
    void seed() {
        temporaryScheduleRepository.deleteAll();
        scheduleAssignmentRepository.deleteAll();
        scheduleTemplateRepository.deleteAll();
        employeeRepository.deleteAll();
        userGroupRepository.deleteAll();
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
        regId = timeCodeRepository.saveAndFlush(reg).getId();

        dayShiftId = createFixedShift("Day 9-5", 540, 1020);
        nightShiftId = createFixedShift("Night", 1320, 1740);
        emergencyShiftId = createFixedShift("Emergency", 0, 720);

        var group = userGroupService.create(new UserGroupDtos.UserGroupRequest("Ops", null, null));
        groupId = group.id();

        var emp = employeeService.create(new EmployeeDtos.EmployeeRequest(
                "E001", "Alice", "Doe", null, null,
                null, null, null,
                EmploymentType.FULL_TIME, LocalDate.of(2024, 1, 1),
                null, null, EmployeeStatus.ACTIVE,
                Set.of(groupId), null));
        employeeId = emp.id();
    }

    private UUID createFixedShift(String name, int start, int end) {
        return shiftService.create(new ShiftDtos.ShiftRequest(
                name, ShiftType.FIXED, "#3b82f6", null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, start, end, null)),
                List.of(), List.of(), List.of(), List.of(),
                null, null)).id();
    }

    private UUID createWeeklyTemplate(String name, UUID monShift, UUID tueShift) {
        return templateService.create(new ScheduleDtos.TemplateRequest(
                name, CycleType.WEEKLY, 7, null,
                List.of(
                        new ScheduleDtos.TemplateDayRequest(0, monShift),
                        new ScheduleDtos.TemplateDayRequest(1, tueShift),
                        new ScheduleDtos.TemplateDayRequest(2, monShift),
                        new ScheduleDtos.TemplateDayRequest(3, monShift),
                        new ScheduleDtos.TemplateDayRequest(4, monShift),
                        new ScheduleDtos.TemplateDayRequest(5, null),
                        new ScheduleDtos.TemplateDayRequest(6, null)),
                null)).id();
    }

    @Test
    void resolves_to_none_when_no_rules() {
        var r = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(r.source()).isEqualTo(ResolvedSchedule.Source.NONE);
        assertThat(r.shiftId()).isNull();
    }

    @Test
    void resolves_via_employee_assignment_to_correct_day_in_cycle() {
        UUID tpl = createWeeklyTemplate("Std", dayShiftId, nightShiftId);
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tpl,
                LocalDate.of(2026, 6, 1), null, 10, null));

        var mon = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(mon.source()).isEqualTo(ResolvedSchedule.Source.EMPLOYEE_ASSIGNMENT);
        assertThat(mon.shiftId()).isEqualTo(dayShiftId);
        assertThat(mon.dayIndex()).isEqualTo(0);

        var tue = resolver.resolve(employeeId, LocalDate.of(2026, 6, 2));
        assertThat(tue.shiftId()).isEqualTo(nightShiftId);
        assertThat(tue.dayIndex()).isEqualTo(1);

        // Saturday → null shift, but the assignment still wins.
        var sat = resolver.resolve(employeeId, LocalDate.of(2026, 6, 6));
        assertThat(sat.source()).isEqualTo(ResolvedSchedule.Source.EMPLOYEE_ASSIGNMENT);
        assertThat(sat.shiftId()).isNull();
        assertThat(sat.dayIndex()).isEqualTo(5);
    }

    @Test
    void employee_assignment_beats_group_assignment() {
        UUID tplDay = createWeeklyTemplate("Day", dayShiftId, dayShiftId);
        UUID tplNight = createWeeklyTemplate("Night", nightShiftId, nightShiftId);

        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.GROUP, groupId, tplNight,
                LocalDate.of(2026, 6, 1), null, 50, null));
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tplDay,
                LocalDate.of(2026, 6, 1), null, 1, null));

        var r = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(r.source()).isEqualTo(ResolvedSchedule.Source.EMPLOYEE_ASSIGNMENT);
        assertThat(r.shiftId()).isEqualTo(dayShiftId);
    }

    @Test
    void temporary_schedule_beats_any_assignment() {
        UUID tpl = createWeeklyTemplate("Std", dayShiftId, dayShiftId);
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tpl,
                LocalDate.of(2026, 6, 1), null, 100, null));

        temporaryService.create(new ScheduleDtos.TemporaryScheduleRequest(
                employeeId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                emergencyShiftId, "Emergency cover", null));

        var r = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(r.source()).isEqualTo(ResolvedSchedule.Source.TEMPORARY);
        assertThat(r.shiftId()).isEqualTo(emergencyShiftId);
    }

    @Test
    void temporary_with_null_shift_is_explicit_day_off() {
        UUID tpl = createWeeklyTemplate("Std", dayShiftId, dayShiftId);
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tpl,
                LocalDate.of(2026, 6, 1), null, 100, null));
        temporaryService.create(new ScheduleDtos.TemporaryScheduleRequest(
                employeeId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                null, "Personal day", null));

        var r = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(r.source()).isEqualTo(ResolvedSchedule.Source.TEMPORARY);
        assertThat(r.shiftId()).isNull();
        assertThat(r.hasShift()).isFalse();
    }

    @Test
    void group_assignment_resolves_when_no_employee_assignment() {
        UUID tpl = createWeeklyTemplate("GroupTpl", nightShiftId, nightShiftId);
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.GROUP, groupId, tpl,
                LocalDate.of(2026, 6, 1), null, 5, null));

        var r = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(r.source()).isEqualTo(ResolvedSchedule.Source.GROUP_ASSIGNMENT);
        assertThat(r.shiftId()).isEqualTo(nightShiftId);
    }

    @Test
    void assignment_outside_window_does_not_apply() {
        UUID tpl = createWeeklyTemplate("Std", dayShiftId, dayShiftId);
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tpl,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 10, null));

        var before = resolver.resolve(employeeId, LocalDate.of(2026, 5, 31));
        assertThat(before.source()).isEqualTo(ResolvedSchedule.Source.NONE);

        var inside = resolver.resolve(employeeId, LocalDate.of(2026, 6, 15));
        assertThat(inside.source()).isEqualTo(ResolvedSchedule.Source.EMPLOYEE_ASSIGNMENT);

        var after = resolver.resolve(employeeId, LocalDate.of(2026, 7, 1));
        assertThat(after.source()).isEqualTo(ResolvedSchedule.Source.NONE);
    }

    @Test
    void higher_priority_assignment_wins_same_target() {
        UUID tplDay = createWeeklyTemplate("Day", dayShiftId, dayShiftId);
        UUID tplNight = createWeeklyTemplate("Night", nightShiftId, nightShiftId);

        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tplDay,
                LocalDate.of(2026, 6, 1), null, 1, null));
        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, tplNight,
                LocalDate.of(2026, 6, 1), null, 100, null));

        var r = resolver.resolve(employeeId, LocalDate.of(2026, 6, 1));
        assertThat(r.shiftId()).isEqualTo(nightShiftId);
    }
}

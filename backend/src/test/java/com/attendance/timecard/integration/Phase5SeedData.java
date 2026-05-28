package com.attendance.timecard.integration;

import com.attendance.device.domain.CredentialType;
import com.attendance.device.domain.IngestionSourceType;
import com.attendance.device.repository.CredentialRepository;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.device.service.CredentialService;
import com.attendance.device.service.IngestionSourceService;
import com.attendance.device.web.CredentialDtos;
import com.attendance.device.web.IngestionSourceDtos;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.organization.web.EmployeeDtos;
import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.domain.CycleType;
import com.attendance.schedule.repository.ScheduleAssignmentRepository;
import com.attendance.schedule.repository.ScheduleTemplateRepository;
import com.attendance.schedule.repository.TemporaryScheduleRepository;
import com.attendance.schedule.service.ScheduleAssignmentService;
import com.attendance.schedule.service.ScheduleTemplateService;
import com.attendance.schedule.web.ScheduleDtos;
import com.attendance.shift.domain.ShiftType;
import com.attendance.shift.repository.ShiftRepository;
import com.attendance.shift.service.ShiftService;
import com.attendance.shift.web.ShiftDtos;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import com.attendance.exception.repository.ExceptionEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds the common cast of fixtures used by the Phase 5 integration tests:
 * a REG time code, a "Day 9-5" shift, a weekly schedule template assigned to
 * one employee, and an active RFID credential for that employee.
 */
@Component
public class Phase5SeedData {

    @Autowired TimeCodeRepository timeCodeRepository;
    @Autowired ShiftService shiftService;
    @Autowired ShiftRepository shiftRepository;
    @Autowired ScheduleTemplateService templateService;
    @Autowired ScheduleAssignmentService assignmentService;
    @Autowired ScheduleTemplateRepository scheduleTemplateRepository;
    @Autowired ScheduleAssignmentRepository scheduleAssignmentRepository;
    @Autowired TemporaryScheduleRepository temporaryScheduleRepository;
    @Autowired EmployeeService employeeService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserGroupRepository userGroupRepository;
    @Autowired IngestionSourceService sourceService;
    @Autowired IngestionSourceRepository sourceRepository;
    @Autowired CredentialService credentialService;
    @Autowired CredentialRepository credentialRepository;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;
    @Autowired PunchEventRepository punchEventRepository;
    @Autowired ExceptionEventRepository exceptionEventRepository;

    public Seeded seedAll() {
        clear();
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

        UUID dayShift = shiftService.create(new ShiftDtos.ShiftRequest(
                "Day 9-5", ShiftType.FIXED, "#3b82f6", null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(),
                null, null)).id();

        UUID templateId = templateService.create(new ScheduleDtos.TemplateRequest(
                "Mon-Fri Day", CycleType.WEEKLY, 7, null,
                List.of(
                        new ScheduleDtos.TemplateDayRequest(0, dayShift),
                        new ScheduleDtos.TemplateDayRequest(1, dayShift),
                        new ScheduleDtos.TemplateDayRequest(2, dayShift),
                        new ScheduleDtos.TemplateDayRequest(3, dayShift),
                        new ScheduleDtos.TemplateDayRequest(4, dayShift),
                        new ScheduleDtos.TemplateDayRequest(5, null),
                        new ScheduleDtos.TemplateDayRequest(6, null)),
                null)).id();

        var emp = employeeService.create(new EmployeeDtos.EmployeeRequest(
                "E001", "Alice", "Doe", null, null,
                null, null, null,
                EmploymentType.FULL_TIME, LocalDate.of(2024, 1, 1),
                null, "America/New_York", EmployeeStatus.ACTIVE,
                Set.of(), null));
        UUID employeeId = emp.id();

        assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, templateId,
                LocalDate.of(2024, 12, 30), null, 0, null));

        var source = sourceService.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Test Source", IngestionSourceType.REST, true, null));

        credentialService.create(employeeId, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "RFID-001", LocalDate.of(2024, 1, 1), null,
                com.attendance.device.domain.CredentialStatus.ACTIVE));

        return new Seeded(regId, dayShift, templateId, employeeId,
                source.source().id(), source.apiKey());
    }

    public void clear() {
        exceptionEventRepository.deleteAll();
        dailyTimeCardRepository.deleteAll();
        punchEventRepository.deleteAll();
        credentialRepository.deleteAll();
        temporaryScheduleRepository.deleteAll();
        scheduleAssignmentRepository.deleteAll();
        scheduleTemplateRepository.deleteAll();
        sourceRepository.deleteAll();
        employeeRepository.deleteAll();
        userGroupRepository.deleteAll();
        shiftRepository.deleteAll();
        timeCodeRepository.deleteAll();
    }

    public record Seeded(UUID regTimeCodeId,
                         UUID dayShiftId,
                         UUID templateId,
                         UUID employeeId,
                         UUID sourceId,
                         String apiKey) {
    }
}

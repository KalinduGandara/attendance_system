package com.attendance.schedule;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.organization.web.EmployeeDtos;
import com.attendance.schedule.repository.TemporaryScheduleRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemporaryScheduleServiceTest {

    @Autowired TemporaryScheduleService service;
    @Autowired EmployeeService employeeService;
    @Autowired ShiftService shiftService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired ShiftRepository shiftRepository;
    @Autowired TimeCodeRepository timeCodeRepository;
    @Autowired TemporaryScheduleRepository repository;

    private UUID employeeId;
    private UUID shiftId;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        employeeRepository.deleteAll();
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

        shiftId = shiftService.create(new ShiftDtos.ShiftRequest(
                "Cover", ShiftType.FIXED, "#3b82f6", null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(), null, null)).id();
        employeeId = employeeService.create(new EmployeeDtos.EmployeeRequest(
                "E001", "Alice", "Doe", null, null, null, null, null,
                EmploymentType.FULL_TIME, LocalDate.of(2024, 1, 1),
                null, null, EmployeeStatus.ACTIVE,
                java.util.Set.of(), null)).id();
    }

    @Test
    void create_round_trip() {
        var created = service.create(new ScheduleDtos.TemporaryScheduleRequest(
                employeeId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3),
                shiftId, "Cover", null));
        assertThat(created.shiftId()).isEqualTo(shiftId);
        assertThat(created.reason()).isEqualTo("Cover");
    }

    @Test
    void null_shift_id_is_explicit_day_off() {
        var created = service.create(new ScheduleDtos.TemporaryScheduleRequest(
                employeeId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                null, "Personal", null));
        assertThat(created.shiftId()).isNull();
    }

    @Test
    void end_before_start_rejected() {
        assertThatThrownBy(() -> service.create(new ScheduleDtos.TemporaryScheduleRequest(
                employeeId, LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 1),
                shiftId, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    @Test
    void unknown_employee_rejected() {
        assertThatThrownBy(() -> service.create(new ScheduleDtos.TemporaryScheduleRequest(
                UUID.randomUUID(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                shiftId, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("employee not found");
    }

    @Test
    void unknown_shift_rejected() {
        assertThatThrownBy(() -> service.create(new ScheduleDtos.TemporaryScheduleRequest(
                employeeId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                UUID.randomUUID(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("shift not found");
    }
}

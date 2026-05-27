package com.attendance.schedule;

import com.attendance.common.error.ApiException;
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
import com.attendance.schedule.service.ScheduleAssignmentService;
import com.attendance.schedule.service.ScheduleTemplateService;
import com.attendance.schedule.web.ScheduleDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduleAssignmentServiceTest {

    @Autowired ScheduleAssignmentService assignmentService;
    @Autowired ScheduleTemplateService templateService;
    @Autowired EmployeeService employeeService;
    @Autowired UserGroupService userGroupService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired UserGroupRepository userGroupRepository;
    @Autowired ScheduleTemplateRepository templateRepository;
    @Autowired ScheduleAssignmentRepository assignmentRepository;

    private UUID templateId;
    private UUID employeeId;
    private UUID groupId;

    @BeforeEach
    void seed() {
        assignmentRepository.deleteAll();
        templateRepository.deleteAll();
        employeeRepository.deleteAll();
        userGroupRepository.deleteAll();

        templateId = templateService.create(new ScheduleDtos.TemplateRequest(
                "Std", CycleType.DAILY, 1, null, List.of(), null)).id();
        groupId = userGroupService.create(
                new UserGroupDtos.UserGroupRequest("Ops", null, null)).id();
        employeeId = employeeService.create(new EmployeeDtos.EmployeeRequest(
                "E001", "Alice", "Doe", null, null, null, null, null,
                EmploymentType.FULL_TIME, LocalDate.of(2024, 1, 1),
                null, null, EmployeeStatus.ACTIVE,
                java.util.Set.of(), null)).id();
    }

    @Test
    void create_employee_assignment_round_trip() {
        var created = assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, templateId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31), 10, null));
        assertThat(created.targetType()).isEqualTo(AssignmentTargetType.EMPLOYEE);
        assertThat(created.priority()).isEqualTo(10);
    }

    @Test
    void create_group_assignment_round_trip() {
        var created = assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.GROUP, groupId, templateId,
                LocalDate.of(2026, 6, 1), null, 0, null));
        assertThat(created.targetType()).isEqualTo(AssignmentTargetType.GROUP);
    }

    @Test
    void end_before_start_rejected() {
        assertThatThrownBy(() -> assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, templateId,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1), 0, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    @Test
    void unknown_template_rejected() {
        assertThatThrownBy(() -> assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, employeeId, UUID.randomUUID(),
                LocalDate.of(2026, 6, 1), null, 0, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("template not found");
    }

    @Test
    void unknown_employee_rejected() {
        assertThatThrownBy(() -> assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.EMPLOYEE, UUID.randomUUID(), templateId,
                LocalDate.of(2026, 6, 1), null, 0, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("target employee not found");
    }

    @Test
    void unknown_group_rejected() {
        assertThatThrownBy(() -> assignmentService.create(new ScheduleDtos.AssignmentRequest(
                AssignmentTargetType.GROUP, UUID.randomUUID(), templateId,
                LocalDate.of(2026, 6, 1), null, 0, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("target group not found");
    }
}

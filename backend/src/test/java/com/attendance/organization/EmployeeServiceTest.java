package com.attendance.organization;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.domain.CustomFieldType;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.CustomFieldDefinitionRepository;
import com.attendance.organization.repository.CustomFieldValueRepository;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.service.CustomFieldService;
import com.attendance.organization.service.DepartmentService;
import com.attendance.organization.service.EmployeeService;
import com.attendance.organization.service.UserGroupService;
import com.attendance.organization.web.CustomFieldDtos;
import com.attendance.organization.web.DepartmentDtos;
import com.attendance.organization.web.EmployeeDtos;
import com.attendance.organization.web.UserGroupDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeServiceTest {

    @Autowired EmployeeService employeeService;
    @Autowired DepartmentService departmentService;
    @Autowired UserGroupService userGroupService;
    @Autowired CustomFieldService customFieldService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired UserGroupRepository userGroupRepository;
    @Autowired CustomFieldValueRepository customFieldValueRepository;
    @Autowired CustomFieldDefinitionRepository customFieldDefinitionRepository;

    @BeforeEach
    void clean() {
        customFieldValueRepository.deleteAll();
        customFieldDefinitionRepository.deleteAll();
        employeeRepository.deleteAll();
        userGroupRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    @Test
    void create_then_get_with_groups_and_department() {
        var dept = departmentService.create(new DepartmentDtos.DepartmentRequest("Eng", null, null));
        var groupA = userGroupService.create(new UserGroupDtos.UserGroupRequest("A", null, null));
        var groupB = userGroupService.create(new UserGroupDtos.UserGroupRequest("B", null, null));

        var emp = employeeService.create(req("E001", "Alice", "Doe", dept.id(), null,
                Set.of(groupA.id(), groupB.id()), null));
        var fetched = employeeService.get(emp.id());

        assertThat(fetched.firstName()).isEqualTo("Alice");
        assertThat(fetched.departmentName()).isEqualTo("Eng");
        assertThat(fetched.groups()).extracting(EmployeeDtos.GroupRef::id)
                .containsExactlyInAnyOrder(groupA.id(), groupB.id());
    }

    @Test
    void duplicate_employee_code_rejected() {
        employeeService.create(req("E001", "Alice", "Doe", null, null, null, null));
        assertThatThrownBy(() -> employeeService.create(req("E001", "Alice", "Smith", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void manager_cycle_blocked_on_update() {
        var a = employeeService.create(req("A", "Aa", "A", null, null, null, null));
        var b = employeeService.create(req("B", "Bb", "B", null, a.id(), null, null));
        var c = employeeService.create(req("C", "Cc", "C", null, b.id(), null, null));

        assertThatThrownBy(() -> employeeService.update(a.id(),
                req("A", "Aa", "A", null, c.id(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void custom_field_values_round_trip() {
        customFieldService.create(new CustomFieldDtos.CustomFieldDefinitionRequest(
                CustomFieldEntityType.EMPLOYEE, "cost_center", "Cost Center",
                CustomFieldType.STRING, false, null, 0));
        customFieldService.create(new CustomFieldDtos.CustomFieldDefinitionRequest(
                CustomFieldEntityType.EMPLOYEE, "level", "Level",
                CustomFieldType.ENUM, false, List.of("L1", "L2", "L3"), 1));

        var emp = employeeService.create(req("E1", "Alice", "Doe", null, null, null,
                Map.of("cost_center", "CC-100", "level", "L2")));

        var fetched = employeeService.get(emp.id());
        assertThat(fetched.customFields()).hasSize(2);
        assertThat(fetched.customFields()).extracting(EmployeeDtos.CustomFieldValueDto::fieldKey,
                        EmployeeDtos.CustomFieldValueDto::stringValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("cost_center", "CC-100"),
                        org.assertj.core.groups.Tuple.tuple("level", "L2"));
    }

    @Test
    void enum_custom_field_rejects_out_of_range_value() {
        customFieldService.create(new CustomFieldDtos.CustomFieldDefinitionRequest(
                CustomFieldEntityType.EMPLOYEE, "level", "Level",
                CustomFieldType.ENUM, false, List.of("L1", "L2"), 0));

        assertThatThrownBy(() -> employeeService.create(req("E2", "x", "y", null, null, null,
                Map.of("level", "L99"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not in allowed options");
    }

    @Test
    void search_filters_by_department_and_status() {
        var d1 = departmentService.create(new DepartmentDtos.DepartmentRequest("D1", null, null));
        var d2 = departmentService.create(new DepartmentDtos.DepartmentRequest("D2", null, null));
        employeeService.create(req("E1", "A", "X", d1.id(), null, null, null));
        employeeService.create(req("E2", "B", "Y", d1.id(), null, null, null));
        employeeService.create(req("E3", "C", "Z", d2.id(), null, null, null));

        var d1Page = employeeService.search(null, d1.id(), null, 0, 50, "lastName", true);
        assertThat(d1Page.getTotalElements()).isEqualTo(2);

        var search = employeeService.search("a", null, EmployeeStatus.ACTIVE, 0, 50, "lastName", true);
        assertThat(search.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    private static EmployeeDtos.EmployeeRequest req(String code, String first, String last,
                                                    UUID deptId, UUID managerId,
                                                    Set<UUID> groups, Map<String, Object> cfs) {
        return new EmployeeDtos.EmployeeRequest(
                code, first, last, null, null,
                deptId, managerId, null,
                EmploymentType.FULL_TIME,
                LocalDate.of(2024, 1, 1),
                null, null, EmployeeStatus.ACTIVE,
                groups, cfs);
    }
}

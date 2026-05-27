package com.attendance.organization;

import com.attendance.identity.domain.Role;
import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.RoleRepository;
import com.attendance.identity.repository.UserRepository;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.organization.service.ManagerScopeService;
import com.attendance.organization.web.EmployeeDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagerScopeServiceTest {

    @Autowired ManagerScopeService scopeService;
    @Autowired EmployeeService employeeService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired UserGroupRepository userGroupRepository;

    @BeforeEach
    void clean() {
        employeeRepository.deleteAll();
        userGroupRepository.deleteAll();
        departmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void admin_sees_all_active_employees() {
        Role admin = ensureRole("ADMIN");
        User u = saveUser("the-admin", admin);

        var e1 = employeeService.create(req("A"));
        var e2 = employeeService.create(req("B"));

        Set<UUID> visible = scopeService.visibleEmployeeIds(u.getId());
        assertThat(visible).containsExactlyInAnyOrder(e1.id(), e2.id());
    }

    @Test
    void manager_sees_self_and_transitive_reports() {
        Role manager = ensureRole("MANAGER");
        var top = employeeService.create(req("MGR"));
        User u = saveUser("manager-user", manager);
        // link top -> user
        employeeService.update(top.id(),
                new EmployeeDtos.EmployeeRequest("MGR", "M", "G", null, null,
                        null, null, u.getId(), EmploymentType.FULL_TIME,
                        LocalDate.of(2024, 1, 1), null, null, EmployeeStatus.ACTIVE,
                        null, null));

        var lieutenant = employeeService.create(reqWithManager("L1", top.id()));
        var report = employeeService.create(reqWithManager("R1", lieutenant.id()));
        var outsider = employeeService.create(req("OTHER"));

        Set<UUID> visible = scopeService.visibleEmployeeIds(u.getId());
        assertThat(visible).containsExactlyInAnyOrder(top.id(), lieutenant.id(), report.id());
        assertThat(visible).doesNotContain(outsider.id());
    }

    @Test
    void user_without_employee_link_sees_nothing() {
        Role employee = ensureRole("EMPLOYEE");
        User u = saveUser("lonely", employee);
        assertThat(scopeService.visibleEmployeeIds(u.getId())).isEmpty();
    }

    private Role ensureRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setSystem(true);
            return roleRepository.save(r);
        });
    }

    private User saveUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@local");
        u.setPasswordHash("x");
        u.setStatus(UserStatus.ACTIVE);
        u.setRoles(Set.of(role));
        return userRepository.save(u);
    }

    private EmployeeDtos.EmployeeRequest req(String code) {
        return new EmployeeDtos.EmployeeRequest(code, code, code, null, null,
                null, null, null, EmploymentType.FULL_TIME,
                LocalDate.of(2024, 1, 1), null, null, EmployeeStatus.ACTIVE,
                null, null);
    }

    private EmployeeDtos.EmployeeRequest reqWithManager(String code, UUID managerId) {
        return new EmployeeDtos.EmployeeRequest(code, code, code, null, null,
                null, managerId, null, EmploymentType.FULL_TIME,
                LocalDate.of(2024, 1, 1), null, null, EmployeeStatus.ACTIVE,
                null, null);
    }
}

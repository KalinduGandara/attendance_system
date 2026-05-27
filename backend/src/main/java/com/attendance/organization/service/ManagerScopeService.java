package com.attendance.organization.service;

import com.attendance.identity.domain.Role;
import com.attendance.identity.domain.User;
import com.attendance.identity.repository.UserRepository;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the set of employee ids visible to a given user, based on the
 * employee/manager hierarchy. Used later for row-level filtering of
 * employee-scoped queries (time cards, schedules, etc.).
 *
 * <p>Rules:
 * <ul>
 *   <li>ADMIN or HR_MANAGER → all active employees</li>
 *   <li>Otherwise, if user is linked to an employee → that employee plus
 *   transitive direct reports via {@code manager_id}</li>
 *   <li>Otherwise → empty set</li>
 * </ul>
 */
@Service
public class ManagerScopeService {

    private static final Set<String> GLOBAL_ROLES = Set.of("ADMIN", "HR_MANAGER");
    private static final int MAX_HOPS = 32;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public ManagerScopeService(UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public Set<UUID> visibleEmployeeIds(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Set.of();
        }
        boolean global = user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(GLOBAL_ROLES::contains);
        if (global) {
            return new HashSet<>(employeeRepository.findActiveEmployeeIds());
        }
        Employee self = employeeRepository.findByUserId(userId).orElse(null);
        if (self == null) {
            return Set.of();
        }
        Set<UUID> visible = new HashSet<>();
        visible.add(self.getId());
        Set<UUID> frontier = Set.of(self.getId());
        int hops = 0;
        while (!frontier.isEmpty() && hops++ < MAX_HOPS) {
            List<UUID> next = employeeRepository.findReportIdsForManagers(frontier);
            next.removeAll(visible);
            if (next.isEmpty()) {
                break;
            }
            visible.addAll(next);
            frontier = new HashSet<>(next);
        }
        return visible;
    }
}

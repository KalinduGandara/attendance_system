package com.attendance.organization.service;

import com.attendance.common.error.ApiException;
import com.attendance.identity.repository.UserRepository;
import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.domain.Department;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.UserGroup;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.web.EmployeeDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final CustomFieldService customFieldService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           UserGroupRepository userGroupRepository,
                           UserRepository userRepository,
                           CustomFieldService customFieldService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.customFieldService = customFieldService;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeDtos.EmployeeSummary> search(String query,
                                                    UUID departmentId,
                                                    EmployeeStatus status,
                                                    int page,
                                                    int size,
                                                    String sortField,
                                                    boolean ascending) {
        Sort sort = Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC,
                resolveSortField(sortField));
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), sort);
        Page<Employee> result = employeeRepository.search(
                blankToNull(query), departmentId, status, pageable);
        Map<UUID, String> deptNames = loadDepartmentNames(result.getContent());
        return result.map(e -> toSummary(e, deptNames));
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.EmployeeResponse get(UUID id) {
        Employee e = findOrThrow(id);
        return toResponse(e);
    }

    /**
     * Returns the set of group ids the employee belongs to. Used by other modules
     * (e.g. schedule resolution) without exposing the {@code Employee} entity.
     */
    @Transactional(readOnly = true)
    public Set<UUID> groupIdsForEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .map(e -> e.getGroups().stream()
                        .map(UserGroup::getId)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Transactional
    public EmployeeDtos.EmployeeResponse create(EmployeeDtos.EmployeeRequest req) {
        if (employeeRepository.existsByEmployeeCode(req.employeeCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Employee code already in use");
        }
        validateUserId(req.userId(), null);
        validateDepartment(req.departmentId());
        validateManager(req.managerId(), null);
        Set<UserGroup> groups = resolveGroups(req.groupIds());

        Employee e = new Employee();
        applyRequest(e, req, groups);
        e = employeeRepository.save(e);
        customFieldService.writeValues(CustomFieldEntityType.EMPLOYEE, e.getId(), req.customFields());
        return toResponse(e);
    }

    @Transactional
    public EmployeeDtos.EmployeeResponse update(UUID id, EmployeeDtos.EmployeeRequest req) {
        Employee e = findOrThrow(id);
        if (!e.getEmployeeCode().equals(req.employeeCode())
                && employeeRepository.existsByEmployeeCode(req.employeeCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Employee code already in use");
        }
        validateUserId(req.userId(), e.getId());
        validateDepartment(req.departmentId());
        validateManager(req.managerId(), e.getId());
        Set<UserGroup> groups = resolveGroups(req.groupIds());
        applyRequest(e, req, groups);
        customFieldService.writeValues(CustomFieldEntityType.EMPLOYEE, e.getId(), req.customFields());
        return toResponse(e);
    }

    @Transactional
    public void delete(UUID id) {
        Employee e = findOrThrow(id);
        customFieldService.deleteAllForEntity(id);
        employeeRepository.delete(e);
    }

    private void applyRequest(Employee e, EmployeeDtos.EmployeeRequest req, Set<UserGroup> groups) {
        e.setEmployeeCode(req.employeeCode().trim());
        e.setFirstName(req.firstName().trim());
        e.setLastName(req.lastName().trim());
        e.setEmail(blankToNull(req.email()));
        e.setPhone(blankToNull(req.phone()));
        e.setDepartmentId(req.departmentId());
        e.setManagerId(req.managerId());
        e.setUserId(req.userId());
        e.setEmploymentType(req.employmentType());
        e.setHireDate(req.hireDate());
        e.setTerminationDate(req.terminationDate());
        e.setTimezone(blankToNull(req.timezone()));
        e.setStatus(req.status() == null ? EmployeeStatus.ACTIVE : req.status());
        e.getGroups().clear();
        e.getGroups().addAll(groups);
    }

    private void validateUserId(UUID userId, UUID employeeId) {
        if (userId == null) {
            return;
        }
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "Linked user not found");
        }
        employeeRepository.findByUserId(userId).ifPresent(existing -> {
            if (employeeId == null || !existing.getId().equals(employeeId)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "User is already linked to another employee");
            }
        });
    }

    private void validateDepartment(UUID departmentId) {
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "Department not found");
        }
    }

    private void validateManager(UUID managerId, UUID employeeId) {
        if (managerId == null) {
            return;
        }
        if (managerId.equals(employeeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Employee cannot manage themselves");
        }
        Employee mgr = employeeRepository.findById(managerId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "validation", "Manager not found"));
        if (employeeId == null) {
            return;
        }
        UUID cursor = mgr.getManagerId();
        int hops = 0;
        while (cursor != null) {
            if (cursor.equals(employeeId)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Move would create a manager cycle");
            }
            cursor = employeeRepository.findById(cursor).map(Employee::getManagerId).orElse(null);
            if (++hops > 64) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Manager chain too deep");
            }
        }
    }

    private Set<UserGroup> resolveGroups(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<UserGroup> found = userGroupRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "One or more groups not found");
        }
        return new HashSet<>(found);
    }

    private Map<UUID, String> loadDepartmentNames(List<Employee> employees) {
        Set<UUID> ids = employees.stream()
                .map(Employee::getDepartmentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (Department d : departmentRepository.findAllById(ids)) {
            names.put(d.getId(), d.getName());
        }
        return names;
    }

    private EmployeeDtos.EmployeeSummary toSummary(Employee e, Map<UUID, String> deptNames) {
        return new EmployeeDtos.EmployeeSummary(
                e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getDepartmentId(), e.getDepartmentId() == null ? null : deptNames.get(e.getDepartmentId()),
                e.getStatus(), e.getEmploymentType(), e.getHireDate());
    }

    private EmployeeDtos.EmployeeResponse toResponse(Employee e) {
        String deptName = e.getDepartmentId() == null ? null : departmentRepository
                .findById(e.getDepartmentId()).map(Department::getName).orElse(null);
        String managerName = e.getManagerId() == null ? null : employeeRepository
                .findById(e.getManagerId())
                .map(m -> m.getFirstName() + " " + m.getLastName())
                .orElse(null);
        String username = e.getUserId() == null ? null : userRepository.findById(e.getUserId())
                .map(u -> u.getUsername()).orElse(null);
        List<EmployeeDtos.GroupRef> groups = e.getGroups().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(g -> new EmployeeDtos.GroupRef(g.getId(), g.getName()))
                .toList();
        List<EmployeeDtos.CustomFieldValueDto> cfs = customFieldService.readValues(e.getId());
        return new EmployeeDtos.EmployeeResponse(
                e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(),
                e.getEmail(), e.getPhone(),
                e.getDepartmentId(), deptName,
                e.getManagerId(), managerName,
                e.getUserId(), username,
                e.getEmploymentType(), e.getHireDate(), e.getTerminationDate(),
                e.getTimezone(), e.getStatus(),
                groups, cfs,
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    private Employee findOrThrow(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Employee not found"));
    }

    private String resolveSortField(String requested) {
        if (requested == null || requested.isBlank()) {
            return "lastName";
        }
        return switch (requested) {
            case "firstName", "lastName", "employeeCode", "hireDate", "status" -> requested;
            default -> "lastName";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

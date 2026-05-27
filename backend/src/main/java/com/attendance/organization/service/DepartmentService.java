package com.attendance.organization.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.Department;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.web.DepartmentDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDtos.DepartmentResponse> list() {
        return departmentRepository.findAll(Sort.by("name"))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentDtos.DepartmentNode> tree() {
        List<Department> all = departmentRepository.findAll();
        Map<UUID, List<Department>> byParent = new HashMap<>();
        for (Department d : all) {
            byParent.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
        }
        for (List<Department> children : byParent.values()) {
            children.sort(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER));
        }
        List<Department> roots = byParent.getOrDefault(null, List.of());
        return roots.stream().map(d -> buildNode(d, byParent)).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDtos.DepartmentResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DepartmentDtos.DepartmentResponse create(DepartmentDtos.DepartmentRequest req) {
        validateParent(req.parentId(), null);
        Department d = new Department();
        d.setName(req.name().trim());
        d.setParentId(req.parentId());
        d.setTimezone(req.timezone());
        return toResponse(departmentRepository.save(d));
    }

    @Transactional
    public DepartmentDtos.DepartmentResponse update(UUID id, DepartmentDtos.DepartmentRequest req) {
        Department d = findOrThrow(id);
        validateParent(req.parentId(), id);
        d.setName(req.name().trim());
        d.setParentId(req.parentId());
        d.setTimezone(req.timezone());
        return toResponse(d);
    }

    @Transactional
    public void delete(UUID id) {
        Department d = findOrThrow(id);
        if (departmentRepository.existsByParentId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Department has child departments — reassign or delete them first");
        }
        if (departmentRepository.hasEmployees(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Department has employees assigned — reassign them first");
        }
        departmentRepository.delete(d);
    }

    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Department cannot be its own parent");
        }
        UUID cursor = parentId;
        int hops = 0;
        while (cursor != null) {
            if (selfId != null && cursor.equals(selfId)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Move would create a cycle in the department tree");
            }
            Department parent = departmentRepository.findById(cursor).orElseThrow(() ->
                    new ApiException(HttpStatus.BAD_REQUEST, "validation",
                            "Parent department not found"));
            cursor = parent.getParentId();
            if (++hops > 64) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Department hierarchy too deep");
            }
        }
    }

    private DepartmentDtos.DepartmentNode buildNode(Department d,
                                                    Map<UUID, List<Department>> byParent) {
        List<DepartmentDtos.DepartmentNode> children = byParent.getOrDefault(d.getId(), List.of())
                .stream().map(c -> buildNode(c, byParent)).toList();
        return new DepartmentDtos.DepartmentNode(d.getId(), d.getName(), d.getTimezone(), children);
    }

    private Department findOrThrow(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Department not found"));
    }

    private DepartmentDtos.DepartmentResponse toResponse(Department d) {
        return new DepartmentDtos.DepartmentResponse(d.getId(), d.getName(), d.getParentId(),
                d.getTimezone(), d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
    }
}

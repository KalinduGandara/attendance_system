package com.attendance.organization.web;

import com.attendance.common.web.PageResponse;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "Search employees")
    public PageResponse<EmployeeDtos.EmployeeSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "lastName") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        boolean asc = !direction.equalsIgnoreCase("desc");
        return PageResponse.of(service.search(q, departmentId, status, page, size, sort, asc));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "Get an employee by id")
    public EmployeeDtos.EmployeeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Create an employee")
    public ResponseEntity<EmployeeDtos.EmployeeResponse> create(
            @Valid @RequestBody EmployeeDtos.EmployeeRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Update an employee")
    public EmployeeDtos.EmployeeResponse update(@PathVariable UUID id,
                                                @Valid @RequestBody EmployeeDtos.EmployeeRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Delete an employee")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.attendance.organization.web;

import com.attendance.organization.service.DepartmentService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "List all departments (flat)")
    public List<DepartmentDtos.DepartmentResponse> list() {
        return service.list();
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "Return departments as a nested tree")
    public List<DepartmentDtos.DepartmentNode> tree() {
        return service.tree();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "Get a department by id")
    public DepartmentDtos.DepartmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Create a department")
    public ResponseEntity<DepartmentDtos.DepartmentResponse> create(
            @Valid @RequestBody DepartmentDtos.DepartmentRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Update a department")
    public DepartmentDtos.DepartmentResponse update(@PathVariable UUID id,
                                                    @Valid @RequestBody DepartmentDtos.DepartmentRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Delete a department")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

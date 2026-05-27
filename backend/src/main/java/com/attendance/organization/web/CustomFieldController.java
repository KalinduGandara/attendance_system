package com.attendance.organization.web;

import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.service.CustomFieldService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/custom-fields")
@Tag(name = "Custom Fields")
public class CustomFieldController {

    private final CustomFieldService service;

    public CustomFieldController(CustomFieldService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "List custom field definitions for an entity type")
    public List<CustomFieldDtos.CustomFieldDefinitionResponse> list(
            @RequestParam(defaultValue = "EMPLOYEE") CustomFieldEntityType entityType) {
        return service.list(entityType);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Create a custom field definition")
    public ResponseEntity<CustomFieldDtos.CustomFieldDefinitionResponse> create(
            @Valid @RequestBody CustomFieldDtos.CustomFieldDefinitionRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Update a custom field definition")
    public CustomFieldDtos.CustomFieldDefinitionResponse update(@PathVariable UUID id,
            @Valid @RequestBody CustomFieldDtos.CustomFieldDefinitionRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Delete a custom field definition")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

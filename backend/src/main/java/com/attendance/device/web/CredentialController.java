package com.attendance.device.web;

import com.attendance.device.service.CredentialService;
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
@RequestMapping("/api/v1/employees/{employeeId}/credentials")
@Tag(name = "Credentials")
public class CredentialController {

    private final CredentialService service;

    public CredentialController(CredentialService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "List credentials for an employee")
    public List<CredentialDtos.CredentialResponse> list(@PathVariable UUID employeeId) {
        return service.listForEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Issue a new credential to an employee")
    public ResponseEntity<CredentialDtos.CredentialResponse> create(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CredentialDtos.CredentialRequest body) {
        return ResponseEntity.status(201).body(service.create(employeeId, body));
    }

    @PutMapping("/{credentialId}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Update a credential")
    public CredentialDtos.CredentialResponse update(@PathVariable UUID employeeId,
                                                    @PathVariable UUID credentialId,
                                                    @Valid @RequestBody CredentialDtos.CredentialRequest body) {
        return service.update(employeeId, credentialId, body);
    }

    @PostMapping("/{credentialId}/revoke")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Revoke a credential")
    public CredentialDtos.CredentialResponse revoke(@PathVariable UUID employeeId,
                                                    @PathVariable UUID credentialId) {
        return service.revoke(employeeId, credentialId);
    }

    @DeleteMapping("/{credentialId}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Delete a credential")
    public ResponseEntity<Void> delete(@PathVariable UUID employeeId,
                                       @PathVariable UUID credentialId) {
        service.delete(employeeId, credentialId);
        return ResponseEntity.noContent().build();
    }
}

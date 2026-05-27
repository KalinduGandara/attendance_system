package com.attendance.shift.web;

import com.attendance.shift.domain.ShiftType;
import com.attendance.shift.service.ShiftService;
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
@RequestMapping("/api/v1/shifts")
@Tag(name = "Shifts")
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('shift.read')")
    @Operation(summary = "List shifts")
    public List<ShiftDtos.ShiftResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ShiftType type,
            @RequestParam(required = false) Boolean active) {
        return service.list(q, type, active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('shift.read')")
    @Operation(summary = "Get a shift with all nested rules")
    public ShiftDtos.ShiftResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('shift.write')")
    @Operation(summary = "Create a shift with nested rules")
    public ResponseEntity<ShiftDtos.ShiftResponse> create(
            @Valid @RequestBody ShiftDtos.ShiftRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('shift.write')")
    @Operation(summary = "Update a shift; children are fully replaced (atomic)")
    public ShiftDtos.ShiftResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody ShiftDtos.ShiftRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('shift.write')")
    @Operation(summary = "Delete a shift (fails if referenced)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

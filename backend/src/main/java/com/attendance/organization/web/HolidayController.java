package com.attendance.organization.web;

import com.attendance.organization.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "Holidays")
public class HolidayController {

    private final HolidayService service;

    public HolidayController(HolidayService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "List holidays, optionally filtered by date range")
    public List<HolidayDtos.HolidayResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.read')")
    @Operation(summary = "Get a holiday by id")
    public HolidayDtos.HolidayResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Create a holiday")
    public ResponseEntity<HolidayDtos.HolidayResponse> create(
            @Valid @RequestBody HolidayDtos.HolidayRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Update a holiday")
    public HolidayDtos.HolidayResponse update(@PathVariable UUID id,
                                              @Valid @RequestBody HolidayDtos.HolidayRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    @Operation(summary = "Delete a holiday")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

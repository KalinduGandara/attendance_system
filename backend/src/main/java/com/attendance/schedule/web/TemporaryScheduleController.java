package com.attendance.schedule.web;

import com.attendance.schedule.service.TemporaryScheduleService;
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
@RequestMapping("/api/v1/temporary-schedules")
@Tag(name = "Temporary schedules")
public class TemporaryScheduleController {

    private final TemporaryScheduleService service;

    public TemporaryScheduleController(TemporaryScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "List temporary schedules; optional filter by employee / date range")
    public List<ScheduleDtos.TemporaryScheduleResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(employeeId, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "Get a temporary schedule")
    public ScheduleDtos.TemporaryScheduleResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Create a per-employee temporary schedule override")
    public ResponseEntity<ScheduleDtos.TemporaryScheduleResponse> create(
            @Valid @RequestBody ScheduleDtos.TemporaryScheduleRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Update a temporary schedule")
    public ScheduleDtos.TemporaryScheduleResponse update(@PathVariable UUID id,
                                                         @Valid @RequestBody ScheduleDtos.TemporaryScheduleRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Delete a temporary schedule")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

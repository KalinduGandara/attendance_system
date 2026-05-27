package com.attendance.schedule.web;

import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.service.ScheduleAssignmentService;
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
@RequestMapping("/api/v1/schedule-assignments")
@Tag(name = "Schedule assignments")
public class ScheduleAssignmentController {

    private final ScheduleAssignmentService service;

    public ScheduleAssignmentController(ScheduleAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "List schedule assignments")
    public List<ScheduleDtos.AssignmentResponse> list(
            @RequestParam(required = false) AssignmentTargetType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) UUID templateId) {
        return service.list(targetType, targetId, templateId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "Get an assignment")
    public ScheduleDtos.AssignmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Create an assignment (employee or group → template, date range)")
    public ResponseEntity<ScheduleDtos.AssignmentResponse> create(
            @Valid @RequestBody ScheduleDtos.AssignmentRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Update an assignment")
    public ScheduleDtos.AssignmentResponse update(@PathVariable UUID id,
                                                  @Valid @RequestBody ScheduleDtos.AssignmentRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Delete an assignment")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

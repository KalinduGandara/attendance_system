package com.attendance.schedule.web;

import com.attendance.schedule.service.ScheduleTemplateService;
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
@RequestMapping("/api/v1/schedule-templates")
@Tag(name = "Schedule templates")
public class ScheduleTemplateController {

    private final ScheduleTemplateService service;

    public ScheduleTemplateController(ScheduleTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "List schedule templates")
    public List<ScheduleDtos.TemplateResponse> list(@RequestParam(required = false) String q) {
        return service.list(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.read')")
    @Operation(summary = "Get a schedule template with day rows")
    public ScheduleDtos.TemplateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Create a schedule template")
    public ResponseEntity<ScheduleDtos.TemplateResponse> create(
            @Valid @RequestBody ScheduleDtos.TemplateRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Update a schedule template (days are fully replaced)")
    public ScheduleDtos.TemplateResponse update(@PathVariable UUID id,
                                                @Valid @RequestBody ScheduleDtos.TemplateRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule.write')")
    @Operation(summary = "Delete a schedule template (fails if referenced)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

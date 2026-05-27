package com.attendance.timecode.web;

import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.service.TimeCodeService;
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
@RequestMapping("/api/v1/time-codes")
@Tag(name = "Time Codes")
public class TimeCodeController {

    private final TimeCodeService service;

    public TimeCodeController(TimeCodeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('timecode.read')")
    @Operation(summary = "List time codes")
    public List<TimeCodeDtos.TimeCodeResponse> list(
            @RequestParam(required = false) TimeCodeCategory category,
            @RequestParam(required = false) Boolean activeOnly) {
        return service.list(category, activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('timecode.read')")
    @Operation(summary = "Get a time code by id")
    public TimeCodeDtos.TimeCodeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('timecode.write')")
    @Operation(summary = "Create a time code")
    public ResponseEntity<TimeCodeDtos.TimeCodeResponse> create(
            @Valid @RequestBody TimeCodeDtos.TimeCodeRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('timecode.write')")
    @Operation(summary = "Update a time code")
    public TimeCodeDtos.TimeCodeResponse update(@PathVariable UUID id,
                                                @Valid @RequestBody TimeCodeDtos.TimeCodeRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('timecode.write')")
    @Operation(summary = "Delete a time code (fails if referenced)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

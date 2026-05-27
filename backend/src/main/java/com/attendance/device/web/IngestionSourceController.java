package com.attendance.device.web;

import com.attendance.device.service.IngestionSourceService;
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
@RequestMapping("/api/v1/ingestion-sources")
@Tag(name = "Ingestion sources")
public class IngestionSourceController {

    private final IngestionSourceService service;

    public IngestionSourceController(IngestionSourceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('device.read')")
    @Operation(summary = "List ingestion sources")
    public List<IngestionSourceDtos.IngestionSourceResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device.read')")
    @Operation(summary = "Get an ingestion source by id")
    public IngestionSourceDtos.IngestionSourceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Create an ingestion source (returns plaintext API key once)")
    public ResponseEntity<IngestionSourceDtos.IngestionSourceWithKey> create(
            @Valid @RequestBody IngestionSourceDtos.IngestionSourceRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Update an ingestion source (does not rotate API key)")
    public IngestionSourceDtos.IngestionSourceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody IngestionSourceDtos.IngestionSourceRequest body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/rotate-key")
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Rotate the API key (returns the new plaintext once)")
    public IngestionSourceDtos.IngestionSourceWithKey rotateKey(@PathVariable UUID id) {
        return service.rotateApiKey(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Delete an ingestion source")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

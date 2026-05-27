package com.attendance.device.web;

import com.attendance.common.web.PageResponse;
import com.attendance.device.domain.DeviceStatus;
import com.attendance.device.service.DeviceService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('device.read')")
    @Operation(summary = "Search devices")
    public PageResponse<DeviceDtos.DeviceResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        boolean asc = !direction.equalsIgnoreCase("desc");
        return PageResponse.of(service.search(q, status, page, size, sort, asc));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device.read')")
    @Operation(summary = "Get a device by id")
    public DeviceDtos.DeviceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Create a device")
    public ResponseEntity<DeviceDtos.DeviceResponse> create(
            @Valid @RequestBody DeviceDtos.DeviceRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Update a device")
    public DeviceDtos.DeviceResponse update(@PathVariable UUID id,
                                            @Valid @RequestBody DeviceDtos.DeviceRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device.write')")
    @Operation(summary = "Delete a device")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

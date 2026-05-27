package com.attendance.device.service;

import com.attendance.common.error.ApiException;
import com.attendance.device.domain.Device;
import com.attendance.device.domain.DeviceStatus;
import com.attendance.device.repository.DeviceRepository;
import com.attendance.device.web.DeviceDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceService {

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };

    private final DeviceRepository repository;
    private final ObjectMapper objectMapper;

    public DeviceService(DeviceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<DeviceDtos.DeviceResponse> search(String q, DeviceStatus status,
                                                 int page, int size,
                                                 String sortField, boolean ascending) {
        Sort sort = Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC,
                resolveSortField(sortField));
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), sort);
        return repository.search(blankToNull(q), status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DeviceDtos.DeviceResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DeviceDtos.DeviceResponse create(DeviceDtos.DeviceRequest req) {
        Device d = new Device();
        apply(d, req);
        return toResponse(repository.save(d));
    }

    @Transactional
    public DeviceDtos.DeviceResponse update(UUID id, DeviceDtos.DeviceRequest req) {
        Device d = findOrThrow(id);
        apply(d, req);
        return toResponse(d);
    }

    @Transactional
    public void delete(UUID id) {
        Device d = findOrThrow(id);
        repository.delete(d);
    }

    private void apply(Device d, DeviceDtos.DeviceRequest req) {
        d.setName(req.name().trim());
        d.setDeviceType(req.deviceType());
        d.setLocation(blankToNull(req.location()));
        d.setStatus(req.status() == null ? DeviceStatus.ACTIVE : req.status());
        d.setCapabilitiesJson(writeJson(req.capabilities()));
    }

    private DeviceDtos.DeviceResponse toResponse(Device d) {
        return new DeviceDtos.DeviceResponse(
                d.getId(), d.getName(), d.getDeviceType(), d.getLocation(), d.getStatus(),
                readJson(d.getCapabilitiesJson()),
                d.getLastSeenAt(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
    }

    private Device findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Device not found"));
    }

    private String writeJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Capabilities must be JSON-serializable");
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private String resolveSortField(String requested) {
        if (requested == null || requested.isBlank()) {
            return "name";
        }
        return switch (requested) {
            case "name", "status", "deviceType", "createdAt", "lastSeenAt" -> requested;
            default -> "name";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

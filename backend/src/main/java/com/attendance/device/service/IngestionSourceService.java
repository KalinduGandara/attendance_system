package com.attendance.device.service;

import com.attendance.common.error.ApiException;
import com.attendance.device.domain.IngestionSource;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.device.web.IngestionSourceDtos;
import com.attendance.platform.security.TokenHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionSourceService {

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };
    private static final SecureRandom RNG = new SecureRandom();
    /** url-safe, no padding; 32 random bytes → 43-char string. */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int API_KEY_BYTES = 32;
    private static final String API_KEY_PREFIX = "atts_";

    private final IngestionSourceRepository repository;
    private final ObjectMapper objectMapper;

    public IngestionSourceService(IngestionSourceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<IngestionSourceDtos.IngestionSourceResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IngestionSourceDtos.IngestionSourceResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public IngestionSourceDtos.IngestionSourceWithKey create(IngestionSourceDtos.IngestionSourceRequest req) {
        IngestionSource s = new IngestionSource();
        apply(s, req);
        String plaintext = generateApiKey();
        s.setApiKeyHash(TokenHasher.sha256(plaintext));
        s = repository.save(s);
        return new IngestionSourceDtos.IngestionSourceWithKey(toResponse(s), plaintext);
    }

    @Transactional
    public IngestionSourceDtos.IngestionSourceResponse update(UUID id, IngestionSourceDtos.IngestionSourceRequest req) {
        IngestionSource s = findOrThrow(id);
        apply(s, req);
        return toResponse(s);
    }

    @Transactional
    public IngestionSourceDtos.IngestionSourceWithKey rotateApiKey(UUID id) {
        IngestionSource s = findOrThrow(id);
        String plaintext = generateApiKey();
        s.setApiKeyHash(TokenHasher.sha256(plaintext));
        return new IngestionSourceDtos.IngestionSourceWithKey(toResponse(s), plaintext);
    }

    @Transactional
    public void delete(UUID id) {
        IngestionSource s = findOrThrow(id);
        repository.delete(s);
    }

    private void apply(IngestionSource s, IngestionSourceDtos.IngestionSourceRequest req) {
        s.setName(req.name().trim());
        s.setSourceType(req.sourceType());
        s.setEnabled(req.enabled() == null ? true : req.enabled());
        s.setConfigJson(writeJson(req.config()));
    }

    private IngestionSourceDtos.IngestionSourceResponse toResponse(IngestionSource s) {
        return new IngestionSourceDtos.IngestionSourceResponse(
                s.getId(), s.getName(), s.getSourceType(), s.isEnabled(),
                readJson(s.getConfigJson()),
                s.getApiKeyHash() != null,
                s.getLastEventAt(), s.getEventsTotal(), s.getEventsRejected(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }

    private IngestionSource findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Ingestion source not found"));
    }

    private String writeJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Config must be JSON-serializable");
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

    private static String generateApiKey() {
        byte[] bytes = new byte[API_KEY_BYTES];
        RNG.nextBytes(bytes);
        return API_KEY_PREFIX + ENCODER.encodeToString(bytes);
    }
}

package com.attendance.device.service;

import com.attendance.device.domain.IngestionSource;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.platform.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves a presented {@code X-Source-Api-Key} header value to its
 * {@link IngestionSource}. Only enabled sources resolve successfully so that
 * disabling a source instantly stops accepting its events.
 */
@Service
public class IngestionSourceAuthService {

    private final IngestionSourceRepository repository;

    public IngestionSourceAuthService(IngestionSourceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IngestionSource> authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByApiKeyHash(TokenHasher.sha256(rawApiKey))
                .filter(IngestionSource::isEnabled);
    }
}

package com.attendance.device.service;

import com.attendance.device.domain.Credential;
import com.attendance.device.domain.CredentialStatus;
import com.attendance.device.domain.CredentialType;
import com.attendance.device.repository.CredentialRepository;
import com.attendance.platform.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a raw credential presentation (type + plaintext value) to the owning
 * employee id. Used by the ingestion adapter in Phase 5. Returns empty when:
 *  - no credential with that (type, lookup) exists,
 *  - the credential is not ACTIVE, or
 *  - the credential is outside its valid_from / valid_to window.
 */
@Service
public class CredentialResolutionService {

    private final CredentialRepository credentialRepository;

    public CredentialResolutionService(CredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveEmployeeId(CredentialType type, String rawValue) {
        return resolveEmployeeId(type, rawValue, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveEmployeeId(CredentialType type, String rawValue, LocalDate onDate) {
        if (type == null || rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String lookup = TokenHasher.sha256(rawValue);
        return credentialRepository
                .findByCredentialTypeAndCredentialLookupAndStatus(type, lookup, CredentialStatus.ACTIVE)
                .filter(c -> withinValidWindow(c, onDate))
                .map(Credential::getEmployeeId);
    }

    private static boolean withinValidWindow(Credential c, LocalDate onDate) {
        LocalDate d = onDate == null ? LocalDate.now() : onDate;
        if (c.getValidFrom() != null && d.isBefore(c.getValidFrom())) {
            return false;
        }
        if (c.getValidTo() != null && d.isAfter(c.getValidTo())) {
            return false;
        }
        return true;
    }
}

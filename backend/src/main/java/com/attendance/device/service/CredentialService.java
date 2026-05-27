package com.attendance.device.service;

import com.attendance.common.error.ApiException;
import com.attendance.device.domain.Credential;
import com.attendance.device.domain.CredentialStatus;
import com.attendance.device.repository.CredentialRepository;
import com.attendance.device.web.CredentialDtos;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.platform.security.TokenHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CredentialService {

    private final CredentialRepository repository;
    private final EmployeeRepository employeeRepository;

    public CredentialService(CredentialRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<CredentialDtos.CredentialResponse> listForEmployee(UUID employeeId) {
        requireEmployee(employeeId);
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CredentialDtos.CredentialResponse create(UUID employeeId, CredentialDtos.CredentialRequest req) {
        requireEmployee(employeeId);
        String lookup = TokenHasher.sha256(req.value());
        if (repository.existsByCredentialTypeAndCredentialLookup(req.credentialType(), lookup)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Another credential of this type already uses that value");
        }
        Credential c = new Credential();
        c.setEmployeeId(employeeId);
        c.setCredentialType(req.credentialType());
        c.setCredentialValue(lookup);
        c.setCredentialLookup(lookup);
        c.setValidFrom(req.validFrom());
        c.setValidTo(req.validTo());
        c.setStatus(req.status() == null ? CredentialStatus.ACTIVE : req.status());
        validateDates(c);
        return toResponse(repository.save(c));
    }

    @Transactional
    public CredentialDtos.CredentialResponse update(UUID employeeId, UUID credentialId,
                                                   CredentialDtos.CredentialRequest req) {
        Credential c = findOwnedOrThrow(employeeId, credentialId);
        String lookup = TokenHasher.sha256(req.value());
        boolean valueChanged = !lookup.equals(c.getCredentialLookup())
                || c.getCredentialType() != req.credentialType();
        if (valueChanged
                && repository.existsByCredentialTypeAndCredentialLookup(req.credentialType(), lookup)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Another credential of this type already uses that value");
        }
        c.setCredentialType(req.credentialType());
        c.setCredentialValue(lookup);
        c.setCredentialLookup(lookup);
        c.setValidFrom(req.validFrom());
        c.setValidTo(req.validTo());
        c.setStatus(req.status() == null ? CredentialStatus.ACTIVE : req.status());
        validateDates(c);
        return toResponse(c);
    }

    @Transactional
    public void delete(UUID employeeId, UUID credentialId) {
        Credential c = findOwnedOrThrow(employeeId, credentialId);
        repository.delete(c);
    }

    @Transactional
    public CredentialDtos.CredentialResponse revoke(UUID employeeId, UUID credentialId) {
        Credential c = findOwnedOrThrow(employeeId, credentialId);
        c.setStatus(CredentialStatus.REVOKED);
        return toResponse(c);
    }

    private void requireEmployee(UUID employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "Employee not found");
        }
    }

    private Credential findOwnedOrThrow(UUID employeeId, UUID credentialId) {
        Credential c = repository.findById(credentialId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Credential not found"));
        if (!c.getEmployeeId().equals(employeeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "Credential not found");
        }
        return c;
    }

    private void validateDates(Credential c) {
        if (c.getValidTo() != null && c.getValidFrom() != null
                && c.getValidTo().isBefore(c.getValidFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "valid_to cannot be before valid_from");
        }
    }

    private CredentialDtos.CredentialResponse toResponse(Credential c) {
        return new CredentialDtos.CredentialResponse(
                c.getId(), c.getEmployeeId(), c.getCredentialType(),
                maskedFromLookup(c.getCredentialLookup()),
                c.getValidFrom(), c.getValidTo(), c.getStatus(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    private static String maskedFromLookup(String lookup) {
        // Lookup is a 64-char SHA-256 hex; show only the last 6 chars as a fingerprint.
        if (lookup == null || lookup.length() < 6) {
            return "******";
        }
        return "******" + lookup.substring(lookup.length() - 6);
    }
}

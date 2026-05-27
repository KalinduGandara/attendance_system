package com.attendance.device.repository;

import com.attendance.device.domain.Credential;
import com.attendance.device.domain.CredentialStatus;
import com.attendance.device.domain.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    List<Credential> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    Optional<Credential> findByCredentialTypeAndCredentialLookup(CredentialType type, String lookup);

    Optional<Credential> findByCredentialTypeAndCredentialLookupAndStatus(CredentialType type,
                                                                         String lookup,
                                                                         CredentialStatus status);

    boolean existsByCredentialTypeAndCredentialLookup(CredentialType type, String lookup);
}

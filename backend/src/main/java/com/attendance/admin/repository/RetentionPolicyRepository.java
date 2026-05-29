package com.attendance.admin.repository;

import com.attendance.admin.domain.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

    Optional<RetentionPolicy> findByEntityType(String entityType);

    List<RetentionPolicy> findAllByOrderByEntityTypeAsc();

    List<RetentionPolicy> findByEnabledTrue();
}

package com.attendance.device.repository;

import com.attendance.device.domain.IngestionSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestionSourceRepository extends JpaRepository<IngestionSource, UUID> {

    List<IngestionSource> findAllByOrderByNameAsc();

    Optional<IngestionSource> findByApiKeyHash(String apiKeyHash);
}

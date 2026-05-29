package com.attendance.report.repository;

import com.attendance.report.domain.ReportJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    List<ReportJob> findTop50ByRequestedByOrderByCreatedAtDesc(UUID requestedBy);

    List<ReportJob> findTop50ByOrderByCreatedAtDesc();

    @Query("SELECT r.id FROM ReportJob r WHERE r.createdAt < :cutoff ORDER BY r.createdAt ASC")
    List<UUID> findIdsOlderThan(@Param("cutoff") Instant cutoff, Pageable pageable);
}

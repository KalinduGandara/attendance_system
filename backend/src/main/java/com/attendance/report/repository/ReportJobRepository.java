package com.attendance.report.repository;

import com.attendance.report.domain.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    List<ReportJob> findTop50ByRequestedByOrderByCreatedAtDesc(UUID requestedBy);

    List<ReportJob> findTop50ByOrderByCreatedAtDesc();
}

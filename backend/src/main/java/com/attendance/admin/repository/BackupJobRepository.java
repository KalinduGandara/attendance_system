package com.attendance.admin.repository;

import com.attendance.admin.domain.BackupJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BackupJobRepository extends JpaRepository<BackupJob, UUID> {

    List<BackupJob> findTop50ByOrderByCreatedAtDesc();
}

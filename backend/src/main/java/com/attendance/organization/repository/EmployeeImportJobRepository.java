package com.attendance.organization.repository;

import com.attendance.organization.domain.EmployeeImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeImportJobRepository extends JpaRepository<EmployeeImportJob, UUID> {
}

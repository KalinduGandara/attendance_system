package com.attendance.organization.repository;

import com.attendance.organization.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByParentIdIsNullOrderByNameAsc();

    List<Department> findAllByParentIdOrderByNameAsc(UUID parentId);

    boolean existsByParentId(UUID parentId);

    @Query("SELECT COUNT(e) > 0 FROM Employee e WHERE e.departmentId = :id")
    boolean hasEmployees(UUID id);
}

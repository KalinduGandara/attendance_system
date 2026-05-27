package com.attendance.organization.repository;

import com.attendance.organization.domain.Employee;
import com.attendance.organization.domain.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);

    Optional<Employee> findByUserId(UUID userId);

    @Query("""
           SELECT e FROM Employee e
           WHERE (:q IS NULL OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(e.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :q, '%')))
             AND (:departmentId IS NULL OR e.departmentId = :departmentId)
             AND (:status IS NULL OR e.status = :status)
           """)
    Page<Employee> search(@Param("q") String q,
                          @Param("departmentId") UUID departmentId,
                          @Param("status") EmployeeStatus status,
                          Pageable pageable);

    @Query("SELECT e.id FROM Employee e WHERE e.managerId = :managerId")
    List<UUID> findDirectReportIds(@Param("managerId") UUID managerId);

    @Query("SELECT e.id FROM Employee e WHERE e.managerId IN :managerIds")
    List<UUID> findReportIdsForManagers(@Param("managerIds") Set<UUID> managerIds);

    @Query("SELECT e.id FROM Employee e WHERE e.status = com.attendance.organization.domain.EmployeeStatus.ACTIVE")
    List<UUID> findActiveEmployeeIds();
}

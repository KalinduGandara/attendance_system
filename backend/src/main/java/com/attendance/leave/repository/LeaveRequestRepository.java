package com.attendance.leave.repository;

import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Query("""
            SELECT r FROM LeaveRequest r
             WHERE (:employeeId IS NULL OR r.employeeId = :employeeId)
               AND (:status     IS NULL OR r.status     = :status)
               AND (:from       IS NULL OR r.endDate   >= :from)
               AND (:to         IS NULL OR r.startDate <= :to)
             ORDER BY r.startDate DESC, r.id DESC
            """)
    List<LeaveRequest> search(@Param("employeeId") UUID employeeId,
                              @Param("status") LeaveRequestStatus status,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    @Query("""
            SELECT r FROM LeaveRequest r
             WHERE r.employeeId = :employeeId
               AND r.status     = com.attendance.leave.domain.LeaveRequestStatus.APPROVED
               AND r.startDate <= :date
               AND r.endDate   >= :date
            """)
    List<LeaveRequest> findApprovedCovering(@Param("employeeId") UUID employeeId,
                                            @Param("date") LocalDate date);
}

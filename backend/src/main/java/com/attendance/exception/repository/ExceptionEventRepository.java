package com.attendance.exception.repository;

import com.attendance.exception.domain.ExceptionEvent;
import com.attendance.exception.domain.ExceptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExceptionEventRepository extends JpaRepository<ExceptionEvent, UUID> {

    List<ExceptionEvent> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    List<ExceptionEvent> findByStatus(ExceptionStatus status);

    @Modifying
    @Query("DELETE FROM ExceptionEvent e WHERE e.employeeId = :employeeId AND e.workDate = :workDate AND e.status = com.attendance.exception.domain.ExceptionStatus.OPEN")
    int deleteOpenForEmployeeOnDate(@Param("employeeId") UUID employeeId,
                                     @Param("workDate") LocalDate workDate);

    @Query("""
            SELECT e FROM ExceptionEvent e
             WHERE (:employeeId IS NULL OR e.employeeId = :employeeId)
               AND (:status     IS NULL OR e.status     = :status)
               AND (:from       IS NULL OR e.workDate  >= :from)
               AND (:to         IS NULL OR e.workDate  <= :to)
             ORDER BY e.workDate DESC, e.severity DESC, e.id DESC
            """)
    List<ExceptionEvent> search(@Param("employeeId") UUID employeeId,
                                @Param("status") ExceptionStatus status,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);
}

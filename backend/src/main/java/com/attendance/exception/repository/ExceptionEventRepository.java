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
}

package com.attendance.schedule.repository;

import com.attendance.schedule.domain.TemporarySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TemporaryScheduleRepository extends JpaRepository<TemporarySchedule, UUID> {

    @Query("""
           SELECT t FROM TemporarySchedule t
           WHERE (:employeeId IS NULL OR t.employeeId = :employeeId)
             AND (:from IS NULL OR t.endDate >= :from)
             AND (:to   IS NULL OR t.startDate <= :to)
           ORDER BY t.startDate DESC
           """)
    List<TemporarySchedule> search(@Param("employeeId") UUID employeeId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
           SELECT t FROM TemporarySchedule t
           WHERE t.employeeId = :employeeId
             AND t.startDate <= :date
             AND t.endDate   >= :date
           """)
    List<TemporarySchedule> findForEmployeeOnDate(@Param("employeeId") UUID employeeId,
                                                  @Param("date") LocalDate date);
}

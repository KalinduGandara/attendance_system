package com.attendance.timecard.repository;

import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyTimeCardRepository extends JpaRepository<DailyTimeCard, UUID> {

    Optional<DailyTimeCard> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    @Query("""
            SELECT d FROM DailyTimeCard d
             WHERE (:employeeId IS NULL OR d.employeeId = :employeeId)
               AND (:status     IS NULL OR d.status     = :status)
               AND (:from       IS NULL OR d.workDate  >= :from)
               AND (:to         IS NULL OR d.workDate  <= :to)
             ORDER BY d.workDate DESC, d.employeeId ASC
            """)
    List<DailyTimeCard> search(@Param("employeeId") UUID employeeId,
                               @Param("status") DailyTimeCardStatus status,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);
}

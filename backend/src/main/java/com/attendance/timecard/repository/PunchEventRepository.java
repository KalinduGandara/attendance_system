package com.attendance.timecard.repository;

import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PunchEventRepository extends JpaRepository<PunchEvent, UUID> {

    Optional<PunchEvent> findByIngestionSourceIdAndExternalEventId(UUID sourceId, String externalEventId);

    @Query("""
            SELECT p FROM PunchEvent p
             WHERE p.employeeId = :employeeId
               AND p.eventTimeUtc >= :from
               AND p.eventTimeUtc <  :to
               AND p.status = com.attendance.timecard.domain.PunchEventStatus.PROCESSED
             ORDER BY p.eventTimeUtc ASC, p.id ASC
            """)
    List<PunchEvent> findProcessedForEmployeeBetween(@Param("employeeId") UUID employeeId,
                                                     @Param("from") Instant from,
                                                     @Param("to") Instant to);

    @Query("""
            SELECT p FROM PunchEvent p
             WHERE (:employeeId IS NULL OR p.employeeId = :employeeId)
               AND (:status IS NULL OR p.status = :status)
               AND (:from IS NULL OR p.eventTimeUtc >= :from)
               AND (:to   IS NULL OR p.eventTimeUtc <  :to)
             ORDER BY p.eventTimeUtc DESC, p.id DESC
            """)
    Page<PunchEvent> search(@Param("employeeId") UUID employeeId,
                            @Param("status") PunchEventStatus status,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);
}

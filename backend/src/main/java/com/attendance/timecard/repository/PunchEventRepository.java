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

    @Query("""
            SELECT p FROM PunchEvent p
             WHERE p.employeeId = :employeeId
               AND p.eventTimeUtc >= :from
               AND p.eventTimeUtc <  :to
             ORDER BY p.eventTimeUtc ASC, p.id ASC
            """)
    List<PunchEvent> findForEmployeeBetween(@Param("employeeId") UUID employeeId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    /**
     * Oldest punch ids past the retention cutoff, excluding any punch referenced
     * by a manual edit (those carry a {@code time_card_edit} FK and must survive
     * for the forensic trail). Used by the retention purge.
     */
    @Query("""
            SELECT p.id FROM PunchEvent p
             WHERE p.eventTimeUtc < :cutoff
               AND p.id NOT IN (SELECT e.punchEventId FROM TimeCardEdit e WHERE e.punchEventId IS NOT NULL)
             ORDER BY p.eventTimeUtc ASC
            """)
    List<UUID> findIdsOlderThan(@Param("cutoff") Instant cutoff, Pageable pageable);
}

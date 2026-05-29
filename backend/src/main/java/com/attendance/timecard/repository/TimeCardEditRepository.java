package com.attendance.timecard.repository;

import com.attendance.timecard.domain.TimeCardEdit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeCardEditRepository extends JpaRepository<TimeCardEdit, UUID> {
    List<TimeCardEdit> findByDailyTimeCardIdOrderByEditedAtAsc(UUID dailyTimeCardId);

    @Query("""
            SELECT e FROM TimeCardEdit e
             WHERE e.editedAt >= :from
               AND e.editedAt <  :to
             ORDER BY e.editedAt ASC, e.id ASC
            """)
    List<TimeCardEdit> findEditedBetween(@Param("from") Instant from,
                                         @Param("to") Instant to);
}

package com.attendance.schedule.repository;

import com.attendance.schedule.domain.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, UUID> {

    Optional<ScheduleTemplate> findByName(String name);

    @Query("""
           SELECT t FROM ScheduleTemplate t
           WHERE (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY t.name ASC
           """)
    List<ScheduleTemplate> search(@Param("q") String q);

    @Query("SELECT COUNT(d) FROM ScheduleTemplateDay d WHERE d.shiftId = :shiftId")
    long countDaysUsingShift(@Param("shiftId") UUID shiftId);
}

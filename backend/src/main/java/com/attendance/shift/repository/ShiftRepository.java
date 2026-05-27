package com.attendance.shift.repository;

import com.attendance.shift.domain.Shift;
import com.attendance.shift.domain.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    @Query("""
           SELECT s FROM Shift s
           WHERE (:q IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')))
             AND (:type IS NULL OR s.shiftType = :type)
             AND (:active IS NULL OR s.active = :active)
           ORDER BY s.name ASC
           """)
    List<Shift> search(@Param("q") String q,
                       @Param("type") ShiftType type,
                       @Param("active") Boolean active);

    @Query("SELECT COUNT(s) FROM Shift s WHERE :shiftId MEMBER OF s.candidateShiftIds")
    long countAsFloatingCandidate(@Param("shiftId") UUID shiftId);
}

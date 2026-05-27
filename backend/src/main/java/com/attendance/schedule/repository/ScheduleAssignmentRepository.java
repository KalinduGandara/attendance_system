package com.attendance.schedule.repository;

import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.domain.ScheduleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, UUID> {

    @Query("""
           SELECT a FROM ScheduleAssignment a
           WHERE (:targetType IS NULL OR a.targetType = :targetType)
             AND (:targetId IS NULL OR a.targetId = :targetId)
             AND (:templateId IS NULL OR a.templateId = :templateId)
           ORDER BY a.startDate DESC, a.priority DESC
           """)
    List<ScheduleAssignment> search(@Param("targetType") AssignmentTargetType targetType,
                                    @Param("targetId") UUID targetId,
                                    @Param("templateId") UUID templateId);

    @Query("""
           SELECT a FROM ScheduleAssignment a
           WHERE a.targetType = com.attendance.schedule.domain.AssignmentTargetType.EMPLOYEE
             AND a.targetId = :employeeId
             AND a.startDate <= :date
             AND (a.endDate IS NULL OR a.endDate >= :date)
           """)
    List<ScheduleAssignment> findEmployeeAssignmentsForDate(@Param("employeeId") UUID employeeId,
                                                            @Param("date") LocalDate date);

    @Query("""
           SELECT a FROM ScheduleAssignment a
           WHERE a.targetType = com.attendance.schedule.domain.AssignmentTargetType.GROUP
             AND a.targetId IN :groupIds
             AND a.startDate <= :date
             AND (a.endDate IS NULL OR a.endDate >= :date)
           """)
    List<ScheduleAssignment> findGroupAssignmentsForDate(@Param("groupIds") Collection<UUID> groupIds,
                                                         @Param("date") LocalDate date);

    long countByTemplateId(UUID templateId);
}

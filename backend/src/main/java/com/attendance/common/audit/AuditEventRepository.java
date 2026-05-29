package com.attendance.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Filtered audit timeline. Any null filter is ignored. {@code to} is an
     * exclusive upper bound; the controller adds a day to make it inclusive.
     */
    @Query("""
            SELECT a FROM AuditEvent a
             WHERE (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
               AND (:action      IS NULL OR a.action      = :action)
               AND (:entityType  IS NULL OR a.entityType  = :entityType)
               AND (:entityId    IS NULL OR a.entityId    = :entityId)
               AND (:from        IS NULL OR a.occurredAt >= :from)
               AND (:to          IS NULL OR a.occurredAt <  :to)
            """)
    Page<AuditEvent> search(@Param("actorUserId") UUID actorUserId,
                            @Param("action") String action,
                            @Param("entityType") String entityType,
                            @Param("entityId") UUID entityId,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);

    @Query("SELECT a.id FROM AuditEvent a WHERE a.occurredAt < :cutoff ORDER BY a.occurredAt ASC")
    List<UUID> findIdsOlderThan(@Param("cutoff") Instant cutoff, Pageable pageable);
}

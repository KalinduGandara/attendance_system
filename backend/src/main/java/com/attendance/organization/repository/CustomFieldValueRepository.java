package com.attendance.organization.repository;

import com.attendance.organization.domain.CustomFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, UUID> {

    List<CustomFieldValue> findAllByEntityId(UUID entityId);

    Optional<CustomFieldValue> findByDefinitionIdAndEntityId(UUID definitionId, UUID entityId);

    @Modifying
    @Query("DELETE FROM CustomFieldValue v WHERE v.definitionId = :definitionId")
    void deleteAllByDefinitionId(@Param("definitionId") UUID definitionId);

    @Modifying
    @Query("DELETE FROM CustomFieldValue v WHERE v.entityId = :entityId")
    void deleteAllByEntityId(@Param("entityId") UUID entityId);
}

package com.attendance.organization.repository;

import com.attendance.organization.domain.CustomFieldDefinition;
import com.attendance.organization.domain.CustomFieldEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, UUID> {

    List<CustomFieldDefinition> findAllByEntityTypeOrderByDisplayOrderAscDisplayLabelAsc(CustomFieldEntityType entityType);

    Optional<CustomFieldDefinition> findByEntityTypeAndFieldKey(CustomFieldEntityType entityType, String fieldKey);

    boolean existsByEntityTypeAndFieldKey(CustomFieldEntityType entityType, String fieldKey);
}

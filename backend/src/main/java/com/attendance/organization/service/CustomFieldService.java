package com.attendance.organization.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.CustomFieldDefinition;
import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.domain.CustomFieldType;
import com.attendance.organization.domain.CustomFieldValue;
import com.attendance.organization.repository.CustomFieldDefinitionRepository;
import com.attendance.organization.repository.CustomFieldValueRepository;
import com.attendance.organization.web.CustomFieldDtos;
import com.attendance.organization.web.EmployeeDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CustomFieldService {

    private final CustomFieldDefinitionRepository definitionRepository;
    private final CustomFieldValueRepository valueRepository;
    private final ObjectMapper objectMapper;

    public CustomFieldService(CustomFieldDefinitionRepository definitionRepository,
                              CustomFieldValueRepository valueRepository,
                              ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.valueRepository = valueRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDtos.CustomFieldDefinitionResponse> list(CustomFieldEntityType type) {
        return definitionRepository
                .findAllByEntityTypeOrderByDisplayOrderAscDisplayLabelAsc(type)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomFieldDtos.CustomFieldDefinitionResponse create(CustomFieldDtos.CustomFieldDefinitionRequest req) {
        if (definitionRepository.existsByEntityTypeAndFieldKey(req.entityType(), req.fieldKey())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Custom field with this key already exists for this entity");
        }
        validateOptions(req.fieldType(), req.options());
        CustomFieldDefinition def = new CustomFieldDefinition();
        def.setEntityType(req.entityType());
        def.setFieldKey(req.fieldKey());
        def.setDisplayLabel(req.displayLabel());
        def.setFieldType(req.fieldType());
        def.setRequired(req.required());
        def.setOptionsJson(serializeOptions(req.options()));
        def.setDisplayOrder(req.displayOrder());
        return toResponse(definitionRepository.save(def));
    }

    @Transactional
    public CustomFieldDtos.CustomFieldDefinitionResponse update(UUID id,
                                                                CustomFieldDtos.CustomFieldDefinitionRequest req) {
        CustomFieldDefinition def = findOrThrow(id);
        if (def.getFieldType() != req.fieldType()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Field type cannot be changed — create a new field instead");
        }
        if (!Objects.equals(def.getFieldKey(), req.fieldKey()) || def.getEntityType() != req.entityType()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Field key and entity type cannot be changed");
        }
        validateOptions(req.fieldType(), req.options());
        def.setDisplayLabel(req.displayLabel());
        def.setRequired(req.required());
        def.setOptionsJson(serializeOptions(req.options()));
        def.setDisplayOrder(req.displayOrder());
        return toResponse(def);
    }

    @Transactional
    public void delete(UUID id) {
        CustomFieldDefinition def = findOrThrow(id);
        valueRepository.deleteAllByDefinitionId(id);
        definitionRepository.delete(def);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDtos.CustomFieldValueDto> readValues(UUID entityId) {
        List<CustomFieldValue> values = valueRepository.findAllByEntityId(entityId);
        if (values.isEmpty()) {
            return List.of();
        }
        Map<UUID, CustomFieldDefinition> defs = new HashMap<>();
        for (CustomFieldValue v : values) {
            defs.computeIfAbsent(v.getDefinitionId(), id ->
                    definitionRepository.findById(id).orElse(null));
        }
        List<EmployeeDtos.CustomFieldValueDto> result = new ArrayList<>();
        for (CustomFieldValue v : values) {
            CustomFieldDefinition def = defs.get(v.getDefinitionId());
            if (def == null) {
                continue;
            }
            result.add(new EmployeeDtos.CustomFieldValueDto(
                    def.getId(),
                    def.getFieldKey(),
                    def.getDisplayLabel(),
                    def.getFieldType().name(),
                    v.getValueString(),
                    v.getValueNumber(),
                    v.getValueDate(),
                    v.getValueBoolean()
            ));
        }
        return result;
    }

    @Transactional
    public void writeValues(CustomFieldEntityType entityType, UUID entityId, Map<String, Object> input) {
        List<CustomFieldDefinition> defs = definitionRepository
                .findAllByEntityTypeOrderByDisplayOrderAscDisplayLabelAsc(entityType);
        Map<String, Object> in = input == null ? Map.of() : input;
        for (CustomFieldDefinition def : defs) {
            Object raw = in.get(def.getFieldKey());
            if (raw == null) {
                if (def.isRequired()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                            "Custom field '" + def.getFieldKey() + "' is required");
                }
                valueRepository.findByDefinitionIdAndEntityId(def.getId(), entityId)
                        .ifPresent(valueRepository::delete);
                continue;
            }
            CustomFieldValue v = valueRepository.findByDefinitionIdAndEntityId(def.getId(), entityId)
                    .orElseGet(() -> {
                        CustomFieldValue n = new CustomFieldValue();
                        n.setDefinitionId(def.getId());
                        n.setEntityId(entityId);
                        return n;
                    });
            v.setValueString(null);
            v.setValueNumber(null);
            v.setValueDate(null);
            v.setValueBoolean(null);
            applyValue(def, v, raw);
            valueRepository.save(v);
        }
    }

    @Transactional
    public void deleteAllForEntity(UUID entityId) {
        valueRepository.deleteAllByEntityId(entityId);
    }

    private void applyValue(CustomFieldDefinition def, CustomFieldValue v, Object raw) {
        try {
            switch (def.getFieldType()) {
                case STRING -> v.setValueString(raw.toString());
                case ENUM -> {
                    String s = raw.toString();
                    List<String> options = parseOptions(def.getOptionsJson());
                    if (!options.isEmpty() && !options.contains(s)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                                "Value for '" + def.getFieldKey() + "' is not in allowed options");
                    }
                    v.setValueString(s);
                }
                case NUMBER -> v.setValueNumber(new BigDecimal(raw.toString()));
                case BOOLEAN -> v.setValueBoolean(parseBoolean(raw));
                case DATE -> v.setValueDate(LocalDate.parse(raw.toString()));
            }
        } catch (NumberFormatException | java.time.format.DateTimeParseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Invalid value for custom field '" + def.getFieldKey() + "'");
        }
    }

    private boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = raw.toString().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private void validateOptions(CustomFieldType type, List<String> options) {
        if (type == CustomFieldType.ENUM && (options == null || options.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "ENUM custom field requires at least one option");
        }
    }

    private String serializeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                    "Failed to encode options");
        }
    }

    private List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private CustomFieldDefinition findOrThrow(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Custom field not found"));
    }

    private CustomFieldDtos.CustomFieldDefinitionResponse toResponse(CustomFieldDefinition d) {
        return new CustomFieldDtos.CustomFieldDefinitionResponse(
                d.getId(), d.getEntityType(), d.getFieldKey(), d.getDisplayLabel(),
                d.getFieldType(), d.isRequired(), parseOptions(d.getOptionsJson()),
                d.getDisplayOrder(), d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
    }
}

package com.attendance.organization.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "custom_field_definition")
@Getter
@Setter
@NoArgsConstructor
@Auditable("CustomFieldDefinition")
@EntityListeners(AuditEntityListener.class)
public class CustomFieldDefinition extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private CustomFieldEntityType entityType = CustomFieldEntityType.EMPLOYEE;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "display_label", nullable = false, length = 128)
    private String displayLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private CustomFieldType fieldType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

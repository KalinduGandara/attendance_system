package com.attendance.organization.domain;

import com.attendance.common.jpa.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "custom_field_value",
       uniqueConstraints = @UniqueConstraint(name = "uk_custom_field_value",
               columnNames = {"definition_id", "entity_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CustomFieldValue extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "definition_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID definitionId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "entity_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID entityId;

    @Column(name = "value_string", columnDefinition = "TEXT")
    private String valueString;

    @Column(name = "value_number", precision = 20, scale = 6)
    private BigDecimal valueNumber;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;
}

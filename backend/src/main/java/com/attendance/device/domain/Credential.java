package com.attendance.device.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credential")
@Getter
@Setter
@NoArgsConstructor
@Auditable("Credential")
@EntityListeners(AuditEntityListener.class)
public class Credential extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "employee_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 16)
    private CredentialType credentialType;

    @Column(name = "credential_value", nullable = false, length = 255)
    private String credentialValue;

    @Column(name = "credential_lookup", nullable = false, columnDefinition = "CHAR(64)")
    private String credentialLookup;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CredentialStatus status = CredentialStatus.ACTIVE;
}

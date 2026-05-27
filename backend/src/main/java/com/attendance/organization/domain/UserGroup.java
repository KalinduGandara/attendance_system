package com.attendance.organization.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import com.attendance.common.jpa.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_group")
@Getter
@Setter
@NoArgsConstructor
@Auditable("UserGroup")
@EntityListeners(AuditEntityListener.class)
public class UserGroup extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "parent_id", columnDefinition = "BINARY(16)")
    private UUID parentId;

    @Column(name = "description", length = 255)
    private String description;
}

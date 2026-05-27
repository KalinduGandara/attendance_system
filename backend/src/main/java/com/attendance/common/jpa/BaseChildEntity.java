package com.attendance.common.jpa;

import com.attendance.common.uuid.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Base class for child rows that are owned (created/replaced/deleted) atomically
 * by their parent aggregate. They carry only an id — no audit columns or
 * version — because they have no independent lifecycle.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseChildEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UuidV7.generate();
        }
    }
}

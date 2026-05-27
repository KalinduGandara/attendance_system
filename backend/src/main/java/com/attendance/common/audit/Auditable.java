package com.attendance.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tags a JPA entity for audit logging. Carries the human-readable entity-type label
 * that ends up in {@code audit_event.entity_type}.
 *
 * <p>Hibernate does NOT pick up the {@code @EntityListeners} meta-annotation here, so
 * every auditable entity must also declare {@code @EntityListeners(AuditEntityListener.class)}
 * directly. This annotation only carries the entity-type label and serves as a tag for
 * audited entities.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Auditable {

    /** Logical entity type recorded in {@code audit_event.entity_type}. Defaults to the class simple name. */
    String value() default "";
}

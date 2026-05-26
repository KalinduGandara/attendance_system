package com.attendance.common.audit;

import jakarta.persistence.EntityListeners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity as auditable: every insert, update, and delete is recorded
 * in {@code audit_event}. Applied via {@code @EntityListeners(AuditEntityListener.class)}.
 *
 * <p>The annotation itself carries no runtime behavior — it tags the entity for
 * documentation and meta-annotation purposes; the listener is registered separately
 * because annotation composition with {@code @EntityListeners} requires Spring/Hibernate
 * support that varies by version.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@EntityListeners(AuditEntityListener.class)
public @interface Auditable {

    /** Logical entity type recorded in {@code audit_event.entity_type}. Defaults to the class simple name. */
    String value() default "";
}

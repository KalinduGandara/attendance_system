package com.attendance.organization;

import com.attendance.common.audit.AuditEventRepository;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.service.DepartmentService;
import com.attendance.organization.web.DepartmentDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code @Auditable} annotation actually wires the JPA entity listener.
 * If this test fails, audit rows are not being written for create/update/delete and the
 * Phase 0 audit mechanism needs to be fixed (likely by declaring {@code @EntityListeners}
 * directly on each entity rather than relying on meta-annotation composition).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditOnOrgEntityTest {

    @Autowired
    DepartmentService departmentService;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    AuditEventRepository auditEventRepository;

    @BeforeEach
    void clean() {
        auditEventRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    @Test
    void creating_a_department_writes_an_audit_event() {
        long before = auditEventRepository.count();
        departmentService.create(new DepartmentDtos.DepartmentRequest("Audit-Dept", null, null));
        long after = auditEventRepository.count();
        assertThat(after - before)
                .as("@Auditable should trigger AuditEntityListener.onCreate")
                .isGreaterThanOrEqualTo(1);
    }
}

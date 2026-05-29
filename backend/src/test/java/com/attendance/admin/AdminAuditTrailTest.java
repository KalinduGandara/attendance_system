package com.attendance.admin;

import com.attendance.admin.domain.SettingType;
import com.attendance.admin.domain.SystemSetting;
import com.attendance.admin.repository.SystemSettingRepository;
import com.attendance.admin.service.SystemSettingService;
import com.attendance.common.audit.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for plan.md §9: a configuration change must leave an audit
 * trail. The settings update commits, so the AFTER_COMMIT audit listener runs —
 * hence this test is intentionally not {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAuditTrailTest {

    @Autowired SystemSettingService service;
    @Autowired SystemSettingRepository settingRepository;
    @Autowired AuditEventRepository auditEventRepository;

    @BeforeEach
    void seed() {
        if (settingRepository.findBySettingKey("org_name").isEmpty()) {
            SystemSetting s = new SystemSetting();
            s.setSettingKey("org_name");
            s.setSettingValue("Before");
            s.setValueType(SettingType.STRING);
            settingRepository.save(s);
        }
    }

    @AfterEach
    void cleanup() {
        settingRepository.deleteAll();
    }

    @Test
    void updating_a_setting_writes_an_audit_event() {
        service.update(Map.of("org_name", "Audited Corp"));

        var audits = auditEventRepository.search(null, "UPDATE", "SystemSetting", null, null, null,
                PageRequest.of(0, 20));

        assertThat(audits.getContent())
                .as("an UPDATE audit_event should exist for the SystemSetting change")
                .isNotEmpty();
        assertThat(service.getString("org_name", "")).isEqualTo("Audited Corp");
    }
}

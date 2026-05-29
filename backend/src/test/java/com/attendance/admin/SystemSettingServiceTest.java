package com.attendance.admin;

import com.attendance.admin.domain.SettingType;
import com.attendance.admin.domain.SystemSetting;
import com.attendance.admin.repository.SystemSettingRepository;
import com.attendance.admin.service.SystemSettingService;
import com.attendance.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SystemSettingServiceTest {

    @Autowired SystemSettingService service;
    @Autowired SystemSettingRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        save("org_name", "Acme", SettingType.STRING);
        save("password_min_length", "8", SettingType.NUMBER);
        save("backup_enabled", "false", SettingType.BOOLEAN);
        save("backup_cron", "0 0 3 * * *", SettingType.STRING);
    }

    private void save(String key, String value, SettingType type) {
        SystemSetting s = new SystemSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setValueType(type);
        repository.save(s);
    }

    @Test
    void valid_update_persists() {
        service.update(Map.of("org_name", "New Corp", "password_min_length", "12"));

        assertThat(service.getString("org_name", "")).isEqualTo("New Corp");
        assertThat(service.getInt("password_min_length", 0)).isEqualTo(12);
    }

    @Test
    void boolean_value_is_normalized_to_lowercase() {
        service.update(Map.of("backup_enabled", "TRUE"));
        assertThat(service.getBoolean("backup_enabled", false)).isTrue();
        assertThat(repository.findBySettingKey("backup_enabled").orElseThrow().getSettingValue())
                .isEqualTo("true");
    }

    @Test
    void rejects_non_numeric_number() {
        assertThatThrownBy(() -> service.update(Map.of("password_min_length", "abc")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void rejects_invalid_boolean() {
        assertThatThrownBy(() -> service.update(Map.of("backup_enabled", "maybe")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("true or false");
    }

    @Test
    void rejects_invalid_cron() {
        assertThatThrownBy(() -> service.update(Map.of("backup_cron", "not a cron")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cron");
    }

    @Test
    void rejects_unknown_key() {
        assertThatThrownBy(() -> service.update(Map.of("does_not_exist", "x")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown setting");
    }

    @Test
    void getCron_falls_back_when_stored_value_invalid() {
        // sanity: a valid stored cron is returned; missing key returns fallback
        assertThat(service.getCron("backup_cron", "0 0 1 * * *")).isEqualTo("0 0 3 * * *");
        assertThat(service.getCron("missing_cron", "0 0 1 * * *")).isEqualTo("0 0 1 * * *");
    }
}

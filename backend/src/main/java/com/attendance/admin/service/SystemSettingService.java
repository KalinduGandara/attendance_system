package com.attendance.admin.service;

import com.attendance.admin.domain.SettingType;
import com.attendance.admin.domain.SystemSetting;
import com.attendance.admin.repository.SystemSettingRepository;
import com.attendance.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Reads and updates org-wide settings with type-aware validation. Settings are
 * audited via the entity listener, so every change leaves an {@code audit_event}.
 * Typed accessors ({@link #getBoolean}, {@link #getCron}, …) back the backup /
 * retention schedulers and tolerate a missing key by returning the default.
 */
@Service
public class SystemSettingService {

    /** Setting keys whose STRING value must be a valid Spring cron expression. */
    private static final String CRON_SUFFIX = "_cron";

    private final SystemSettingRepository repository;
    private final ObjectMapper objectMapper;

    public SystemSettingService(SystemSettingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> getAll() {
        return repository.findAllByOrderBySettingKeyAsc();
    }

    /**
     * Applies a partial map of key → value. Unknown keys and type-invalid values
     * are rejected (400) before any change is persisted.
     */
    @Transactional
    public List<SystemSetting> update(Map<String, String> changes) {
        if (changes == null || changes.isEmpty()) {
            return getAll();
        }
        changes.forEach((key, value) -> {
            SystemSetting setting = repository.findBySettingKey(key)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "validation",
                            "Unknown setting: " + key));
            String normalized = validate(key, value, setting.getValueType());
            setting.setSettingValue(normalized);
        });
        return getAll();
    }

    public String getString(String key, String fallback) {
        return repository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return repository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        try {
            return repository.findBySettingKey(key)
                    .map(SystemSetting::getSettingValue)
                    .map(Integer::parseInt)
                    .orElse(fallback);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Returns the stored cron if valid, otherwise the fallback. */
    public String getCron(String key, String fallback) {
        String value = getString(key, fallback);
        return CronExpression.isValidExpression(value) ? value : fallback;
    }

    private String validate(String key, String rawValue, SettingType type) {
        if (rawValue == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Setting " + key + " cannot be null");
        }
        String value = rawValue.trim();
        switch (type) {
            case NUMBER -> {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException ex) {
                    throw bad(key, "must be numeric");
                }
            }
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw bad(key, "must be true or false");
                }
                value = value.toLowerCase();
            }
            case JSON -> {
                try {
                    objectMapper.readTree(value);
                } catch (Exception ex) {
                    throw bad(key, "must be valid JSON");
                }
            }
            case STRING -> {
                if (key.endsWith(CRON_SUFFIX) && !CronExpression.isValidExpression(value)) {
                    throw bad(key, "must be a valid cron expression");
                }
            }
        }
        return value;
    }

    private ApiException bad(String key, String why) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation", "Setting " + key + " " + why);
    }
}

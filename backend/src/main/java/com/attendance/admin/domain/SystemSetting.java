package com.attendance.admin.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One org-wide configuration value. Audited because settings changes are
 * forensically relevant (they alter system behavior such as backup schedules).
 */
@Entity
@Table(name = "system_setting")
@Getter
@Setter
@NoArgsConstructor
@Auditable("SystemSetting")
@EntityListeners(AuditEntityListener.class)
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, length = 64, updatable = false)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT", nullable = false)
    private String settingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingType valueType = SettingType.STRING;

    @Column(name = "description", length = 255)
    private String description;
}

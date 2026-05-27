package com.attendance.device.domain;

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

import java.time.Instant;

@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
@Auditable("Device")
@EntityListeners(AuditEntityListener.class)
public class Device extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private DeviceType deviceType = DeviceType.SIMULATED;

    @Column(name = "location", length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @Column(name = "capabilities_json", nullable = false, columnDefinition = "TEXT")
    private String capabilitiesJson = "{}";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}

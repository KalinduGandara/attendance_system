package com.attendance.device;

import com.attendance.common.error.ApiException;
import com.attendance.device.domain.DeviceStatus;
import com.attendance.device.domain.DeviceType;
import com.attendance.device.repository.DeviceRepository;
import com.attendance.device.service.DeviceService;
import com.attendance.device.web.DeviceDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceServiceTest {

    @Autowired DeviceService deviceService;
    @Autowired DeviceRepository deviceRepository;

    @BeforeEach
    void clean() {
        deviceRepository.deleteAll();
    }

    @Test
    void create_then_get_round_trip_with_capabilities_json() {
        var created = deviceService.create(new DeviceDtos.DeviceRequest(
                "Lobby Reader", DeviceType.REST_VIRTUAL, "Front lobby",
                DeviceStatus.ACTIVE,
                Map.of("check_in", true, "break", true, "face", false)));

        var fetched = deviceService.get(created.id());
        assertThat(fetched.name()).isEqualTo("Lobby Reader");
        assertThat(fetched.deviceType()).isEqualTo(DeviceType.REST_VIRTUAL);
        assertThat(fetched.capabilities()).containsEntry("check_in", true).containsEntry("face", false);
    }

    @Test
    void update_replaces_fields() {
        var created = deviceService.create(new DeviceDtos.DeviceRequest(
                "Old", DeviceType.SIMULATED, null, DeviceStatus.ACTIVE, Map.of()));
        var updated = deviceService.update(created.id(), new DeviceDtos.DeviceRequest(
                "New", DeviceType.SIMULATED, "Warehouse", DeviceStatus.INACTIVE, Map.of("door", "A")));
        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.status()).isEqualTo(DeviceStatus.INACTIVE);
        assertThat(updated.location()).isEqualTo("Warehouse");
        assertThat(updated.capabilities()).containsEntry("door", "A");
    }

    @Test
    void search_filters_by_status_and_query() {
        deviceService.create(new DeviceDtos.DeviceRequest(
                "Front Gate", DeviceType.REST_VIRTUAL, null, DeviceStatus.ACTIVE, Map.of()));
        deviceService.create(new DeviceDtos.DeviceRequest(
                "Back Gate", DeviceType.REST_VIRTUAL, null, DeviceStatus.INACTIVE, Map.of()));
        deviceService.create(new DeviceDtos.DeviceRequest(
                "Warehouse Scanner", DeviceType.SIMULATED, null, DeviceStatus.ACTIVE, Map.of()));

        var activeOnly = deviceService.search(null, DeviceStatus.ACTIVE, 0, 50, "name", true);
        assertThat(activeOnly.getTotalElements()).isEqualTo(2);

        var queryGate = deviceService.search("gate", null, 0, 50, "name", true);
        assertThat(queryGate.getTotalElements()).isEqualTo(2);
    }

    @Test
    void get_unknown_throws_not_found() {
        assertThatThrownBy(() -> deviceService.get(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}

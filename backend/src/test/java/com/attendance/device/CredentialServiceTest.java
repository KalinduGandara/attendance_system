package com.attendance.device;

import com.attendance.common.error.ApiException;
import com.attendance.device.domain.CredentialStatus;
import com.attendance.device.domain.CredentialType;
import com.attendance.device.repository.CredentialRepository;
import com.attendance.device.service.CredentialResolutionService;
import com.attendance.device.service.CredentialService;
import com.attendance.device.web.CredentialDtos;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.organization.web.EmployeeDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CredentialServiceTest {

    @Autowired CredentialService credentialService;
    @Autowired CredentialResolutionService resolutionService;
    @Autowired EmployeeService employeeService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired CredentialRepository credentialRepository;

    @BeforeEach
    void clean() {
        credentialRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void create_and_resolve_round_trip() {
        UUID empId = newEmployee("E1").id();
        var created = credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "0x4F2A1C", LocalDate.now().minusDays(1), null, null));

        assertThat(created.valueMasked()).startsWith("******");
        assertThat(created.valueMasked()).doesNotContain("0x4F2A1C");

        assertThat(resolutionService.resolveEmployeeId(CredentialType.RFID, "0x4F2A1C"))
                .contains(empId);
    }

    @Test
    void duplicate_credential_value_within_type_is_rejected() {
        UUID a = newEmployee("E1").id();
        UUID b = newEmployee("E2").id();

        credentialService.create(a, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "abc123", LocalDate.now(), null, null));
        assertThatThrownBy(() -> credentialService.create(b, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "abc123", LocalDate.now(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already uses");
    }

    @Test
    void same_value_allowed_across_different_credential_types() {
        UUID empId = newEmployee("E1").id();
        credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "1234", LocalDate.now(), null, null));
        credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.PIN, "1234", LocalDate.now(), null, null));

        assertThat(credentialService.listForEmployee(empId)).hasSize(2);
    }

    @Test
    void revoked_credential_does_not_resolve() {
        UUID empId = newEmployee("E1").id();
        var created = credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.QR, "QR-XYZ", LocalDate.now(), null, null));

        credentialService.revoke(empId, created.id());

        assertThat(resolutionService.resolveEmployeeId(CredentialType.QR, "QR-XYZ")).isEmpty();
    }

    @Test
    void credential_outside_valid_window_does_not_resolve() {
        UUID empId = newEmployee("E1").id();
        credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "future-card",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), null));

        assertThat(resolutionService.resolveEmployeeId(CredentialType.RFID, "future-card"))
                .isEmpty();

        assertThat(resolutionService.resolveEmployeeId(CredentialType.RFID, "future-card",
                LocalDate.now().plusDays(15))).contains(empId);
    }

    @Test
    void resolution_returns_empty_for_unknown_value() {
        assertThat(resolutionService.resolveEmployeeId(CredentialType.RFID, "never-issued"))
                .isEmpty();
    }

    @Test
    void update_value_recomputes_lookup_hash() {
        UUID empId = newEmployee("E1").id();
        var created = credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.QR, "first", LocalDate.now(), null, null));

        credentialService.update(empId, created.id(), new CredentialDtos.CredentialRequest(
                CredentialType.QR, "second", LocalDate.now(), null, CredentialStatus.ACTIVE));

        assertThat(resolutionService.resolveEmployeeId(CredentialType.QR, "first")).isEmpty();
        assertThat(resolutionService.resolveEmployeeId(CredentialType.QR, "second")).contains(empId);
    }

    @Test
    void create_for_unknown_employee_fails() {
        assertThatThrownBy(() -> credentialService.create(UUID.randomUUID(),
                new CredentialDtos.CredentialRequest(
                        CredentialType.RFID, "x", LocalDate.now(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void invalid_validity_window_is_rejected() {
        UUID empId = newEmployee("E1").id();
        assertThatThrownBy(() -> credentialService.create(empId, new CredentialDtos.CredentialRequest(
                CredentialType.RFID, "x", LocalDate.now(), LocalDate.now().minusDays(1), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("valid_to");
    }

    private EmployeeDtos.EmployeeResponse newEmployee(String code) {
        return employeeService.create(new EmployeeDtos.EmployeeRequest(
                code, "First", "Last", null, null, null, null, null,
                EmploymentType.FULL_TIME, LocalDate.of(2024, 1, 1),
                null, null, null, null, null));
    }
}

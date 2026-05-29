package com.attendance.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerSecurityTest {

    @Autowired MockMvc mvc;

    // ---------- audit log: audit.read ----------

    @Test
    void audit_anonymous_returns_401() throws Exception {
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"report.run"})
    void audit_without_audit_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"audit.read"})
    void audit_with_audit_read_returns_200() throws Exception {
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isOk());
    }

    // ---------- settings / backups / retention: system.admin ----------

    @Test
    @WithMockUser(authorities = {"audit.read"})
    void settings_with_only_audit_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/system/settings")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"system.admin"})
    void settings_with_system_admin_returns_200() throws Exception {
        mvc.perform(get("/api/v1/system/settings")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"audit.read"})
    void run_backup_without_system_admin_returns_403() throws Exception {
        mvc.perform(post("/api/v1/system/backups")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"system.admin"})
    void list_backups_with_system_admin_returns_200() throws Exception {
        mvc.perform(get("/api/v1/system/backups")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"system.admin"})
    void list_retention_policies_with_system_admin_returns_200() throws Exception {
        mvc.perform(get("/api/v1/system/retention-policies")).andExpect(status().isOk());
    }
}

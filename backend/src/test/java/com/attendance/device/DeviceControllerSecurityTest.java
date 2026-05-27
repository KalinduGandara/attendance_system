package com.attendance.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void unauthenticated_devices_list_returns_401() throws Exception {
        mvc.perform(get("/api/v1/devices")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"employee.read"})
    void user_without_device_read_gets_403() throws Exception {
        mvc.perform(get("/api/v1/devices")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"device.read"})
    void device_read_can_list() throws Exception {
        mvc.perform(get("/api/v1/devices")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"device.read"})
    void create_requires_device_write() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "X", "deviceType", "SIMULATED"));
        mvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"device.read"})
    void ingestion_sources_list_requires_device_read_works() throws Exception {
        mvc.perform(get("/api/v1/ingestion-sources")).andExpect(status().isOk());
    }
}

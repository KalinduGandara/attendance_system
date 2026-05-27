package com.attendance.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(get("/api/v1/schedule-templates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/schedule-assignments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/temporary-schedules")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"shift.read"})
    void without_schedule_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/schedule-templates")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedule-assignments")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/temporary-schedules")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"schedule.read"})
    void with_schedule_read_can_list_all() throws Exception {
        mvc.perform(get("/api/v1/schedule-templates")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/schedule-assignments")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/temporary-schedules")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"schedule.read"})
    void create_template_requires_schedule_write() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "X",
                "cycleType", "DAILY",
                "cycleLengthDays", 1,
                "days", java.util.List.of()));
        mvc.perform(post("/api/v1/schedule-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"schedule.read"})
    void create_assignment_requires_schedule_write() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "EMPLOYEE",
                "targetId", UUID.randomUUID().toString(),
                "templateId", UUID.randomUUID().toString(),
                "startDate", "2026-06-01"));
        mvc.perform(post("/api/v1/schedule-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"schedule.read"})
    void create_temporary_requires_schedule_write() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", UUID.randomUUID().toString(),
                "startDate", "2026-06-01",
                "endDate", "2026-06-01"));
        mvc.perform(post("/api/v1/temporary-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"schedule.read"})
    void resolution_endpoint_works_with_read_only() throws Exception {
        mvc.perform(get("/api/v1/schedule-resolution")
                        .param("employeeId", UUID.randomUUID().toString())
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-03"))
                .andExpect(status().isOk());
    }
}

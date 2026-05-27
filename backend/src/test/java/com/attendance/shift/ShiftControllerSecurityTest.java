package com.attendance.shift;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(get("/api/v1/shifts")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"timecode.read"})
    void without_shift_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/shifts")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"shift.read"})
    void with_shift_read_can_list() throws Exception {
        mvc.perform(get("/api/v1/shifts")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"shift.read"})
    void create_requires_shift_write() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "X", "shiftType", "FIXED", "color", "#000000",
                "attendanceTimeCodeId", "00000000-0000-0000-0000-000000000000",
                "segments", java.util.List.of(),
                "roundingRules", java.util.List.of(),
                "graceRules", java.util.List.of(),
                "breakRules", java.util.List.of(),
                "overtimeRules", java.util.List.of()));
        mvc.perform(post("/api/v1/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}

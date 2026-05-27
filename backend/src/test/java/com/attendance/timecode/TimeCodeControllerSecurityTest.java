package com.attendance.timecode;

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
class TimeCodeControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(get("/api/v1/time-codes")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"shift.read"})
    void without_timecode_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/time-codes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"timecode.read"})
    void with_timecode_read_can_list() throws Exception {
        mvc.perform(get("/api/v1/time-codes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"timecode.read"})
    void create_requires_timecode_write() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "code", "X", "name", "X", "category", "ATTENDANCE",
                "rate", "1.00", "color", "#000000",
                "paid", true, "countsForAttendance", true));
        mvc.perform(post("/api/v1/time-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}

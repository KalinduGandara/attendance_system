package com.attendance.timecard.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PunchIngestionControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void anonymous_returns_401() throws Exception {
        mvc.perform(post("/api/v1/ingestion/punches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void wrong_permission_returns_403() throws Exception {
        var body = java.util.Map.of(
                "sourceId", UUID.randomUUID().toString(),
                "events", List.of(java.util.Map.of(
                        "externalEventId", "x",
                        "eventType", "CHECK_IN",
                        "eventTime", "2026-05-28T13:00:00Z")));
        mvc.perform(post("/api/v1/ingestion/punches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}

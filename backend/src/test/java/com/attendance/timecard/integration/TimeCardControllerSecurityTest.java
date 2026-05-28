package com.attendance.timecard.integration;

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
class TimeCardControllerSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void timecards_anonymous_returns_401() throws Exception {
        mvc.perform(get("/api/v1/timecards")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ingestion.write"})
    void timecards_without_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/timecards")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void timecards_with_read_returns_200() throws Exception {
        mvc.perform(get("/api/v1/timecards")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void punch_events_with_read_returns_200() throws Exception {
        mvc.perform(get("/api/v1/punch-events")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void recompute_requires_timecard_edit() throws Exception {
        mvc.perform(post("/api/v1/admin/recompute")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void timecard_edits_anonymous_returns_401() throws Exception {
        mvc.perform(post("/api/v1/timecards/00000000-0000-0000-0000-000000000000/edits")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"changeType\":\"NOTE\",\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void timecard_edits_without_edit_permission_returns_403() throws Exception {
        mvc.perform(post("/api/v1/timecards/00000000-0000-0000-0000-000000000000/edits")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"changeType\":\"NOTE\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"timecard.edit"})
    void timecard_edits_missing_reason_returns_400() throws Exception {
        mvc.perform(post("/api/v1/timecards/00000000-0000-0000-0000-000000000000/edits")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"changeType\":\"NOTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void punch_assign_requires_edit_permission() throws Exception {
        mvc.perform(post("/api/v1/punch-events/00000000-0000-0000-0000-000000000000/assign")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"00000000-0000-0000-0000-000000000000\"}"))
                .andExpect(status().isForbidden());
    }
}

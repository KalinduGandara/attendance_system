package com.attendance.report;

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
class ReportControllerSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void list_anonymous_returns_401() throws Exception {
        mvc.perform(get("/api/v1/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void list_without_report_run_returns_403() throws Exception {
        mvc.perform(get("/api/v1/reports")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"report.run"})
    void list_with_report_run_returns_200() throws Exception {
        mvc.perform(get("/api/v1/reports")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void run_without_report_run_returns_403() throws Exception {
        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"DAILY_SUMMARY\",\"parameters\":{\"from\":\"2026-05-01\",\"to\":\"2026-05-31\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"report.run"})
    void run_with_report_run_returns_202() throws Exception {
        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"DAILY_SUMMARY\",\"parameters\":{\"from\":\"2026-05-01\",\"to\":\"2026-05-31\"}}"))
                .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(authorities = {"report.run"})
    void run_without_report_type_returns_400() throws Exception {
        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":{\"from\":\"2026-05-01\",\"to\":\"2026-05-31\"}}"))
                .andExpect(status().isBadRequest());
    }
}

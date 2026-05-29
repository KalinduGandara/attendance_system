package com.attendance.leave;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveControllerSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void leaveTypes_anonymous_returns_401() throws Exception {
        mvc.perform(get("/api/v1/leave-types")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void leaveTypes_without_leave_read_returns_403() throws Exception {
        mvc.perform(get("/api/v1/leave-types")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"leave.read"})
    void leaveTypes_with_leave_read_returns_200() throws Exception {
        mvc.perform(get("/api/v1/leave-types")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"leave.read"})
    void create_leave_type_requires_approve_permission() throws Exception {
        mvc.perform(post("/api/v1/leave-types")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"timeCodeId\":\"00000000-0000-0000-0000-000000000000\",\"defaultAnnualDays\":1,\"requiresApproval\":false,\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"leave.read"})
    void list_requests_with_leave_read_returns_200() throws Exception {
        mvc.perform(get("/api/v1/leave-requests")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"leave.read"})
    void approve_requires_leave_approve() throws Exception {
        mvc.perform(post("/api/v1/leave-requests/00000000-0000-0000-0000-000000000000/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exceptions_anonymous_returns_401() throws Exception {
        mvc.perform(get("/api/v1/exceptions")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"exception.read"})
    void exceptions_with_read_returns_200() throws Exception {
        mvc.perform(get("/api/v1/exceptions")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"exception.read"})
    void resolve_requires_exception_resolve_permission() throws Exception {
        mvc.perform(patch("/api/v1/exceptions/00000000-0000-0000-0000-000000000000/resolve")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"exception.resolve"})
    void resolve_with_open_status_returns_400() throws Exception {
        mvc.perform(patch("/api/v1/exceptions/00000000-0000-0000-0000-000000000000/resolve")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest());
    }
}

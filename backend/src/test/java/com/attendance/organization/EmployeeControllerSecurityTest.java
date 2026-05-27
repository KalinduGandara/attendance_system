package com.attendance.organization;

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
class EmployeeControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void unauthenticated_request_is_rejected_with_401() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"timecard.read"})
    void user_without_employee_read_permission_gets_403() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"employee.read"})
    void user_with_read_permission_can_list() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"employee.read"})
    void create_requires_write_permission() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "employeeCode", "E1",
                "firstName", "A",
                "lastName", "B",
                "employmentType", "FULL_TIME",
                "hireDate", "2024-01-01"));
        mvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}

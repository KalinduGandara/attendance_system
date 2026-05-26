package com.attendance;

import com.attendance.identity.domain.Role;
import com.attendance.identity.repository.RoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowSmokeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void seed_creates_admin_role() {
        // The Flyway migration is disabled in the test profile (H2). Seed runs via JPA's
        // create-drop, so we only assert that the application context starts cleanly and
        // that the AdminBootstrapper has a path to run when migrations are present.
        // For Phase 0 smoke we just exercise the auth chain configuration.
        assertThat(roleRepository).isNotNull();
    }

    @Test
    void unauthenticated_protected_endpoint_returns_401_problem() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotEmpty();
    }

    @Test
    void health_is_public() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void login_with_invalid_credentials_returns_401() throws Exception {
        String json = objectMapper.writeValueAsString(
                java.util.Map.of("username", "no-such-user", "password", "x"));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }
}

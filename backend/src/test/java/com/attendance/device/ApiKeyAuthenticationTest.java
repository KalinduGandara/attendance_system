package com.attendance.device;

import com.attendance.device.domain.IngestionSourceType;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.device.service.IngestionSourceService;
import com.attendance.device.web.IngestionSourceDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the {@code X-Source-Api-Key} authentication filter against
 * a small stub ingestion endpoint. Replaces a JWT-based caller with API-key auth
 * so that Phase 5's {@code POST /ingestion/punches} can rely on the same plumbing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyAuthenticationTest {

    @Autowired MockMvc mvc;
    @Autowired IngestionSourceService sourceService;
    @Autowired IngestionSourceRepository sourceRepository;

    @BeforeEach
    void clean() {
        sourceRepository.deleteAll();
    }

    @Test
    void no_api_key_and_no_jwt_returns_401() throws Exception {
        mvc.perform(get("/api/v1/ingestion/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void valid_api_key_resolves_to_source_principal() throws Exception {
        var created = sourceService.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Test Source", IngestionSourceType.REST, true, null));

        mvc.perform(get("/api/v1/ingestion/ping")
                        .header("X-Source-Api-Key", created.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("API_KEY"))
                .andExpect(jsonPath("$.sourceName").value("Test Source"));
    }

    @Test
    void wrong_api_key_returns_401() throws Exception {
        sourceService.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Test", IngestionSourceType.REST, true, null));

        mvc.perform(get("/api/v1/ingestion/ping")
                        .header("X-Source-Api-Key", "atts_definitely-not-real"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotating_key_invalidates_prior_key() throws Exception {
        var created = sourceService.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Test", IngestionSourceType.REST, true, null));
        String oldKey = created.apiKey();

        var rotated = sourceService.rotateApiKey(created.source().id());

        mvc.perform(get("/api/v1/ingestion/ping").header("X-Source-Api-Key", oldKey))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ingestion/ping").header("X-Source-Api-Key", rotated.apiKey()))
                .andExpect(status().isOk());
    }

    @Test
    void disabled_source_cannot_authenticate() throws Exception {
        var created = sourceService.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Paused", IngestionSourceType.REST, true, null));

        sourceService.update(created.source().id(), new IngestionSourceDtos.IngestionSourceRequest(
                "Paused", IngestionSourceType.REST, false, null));

        mvc.perform(get("/api/v1/ingestion/ping").header("X-Source-Api-Key", created.apiKey()))
                .andExpect(status().isUnauthorized());
    }
}

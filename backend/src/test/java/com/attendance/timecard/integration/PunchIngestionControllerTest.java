package com.attendance.timecard.integration;

import com.attendance.device.domain.CredentialType;
import com.attendance.ingestion.web.PunchIngestionDtos;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.repository.PunchEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PunchIngestionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Phase5SeedData seed;
    @Autowired PunchEventRepository punchEventRepository;

    private Phase5SeedData.Seeded data;

    @BeforeEach
    void setUp() {
        data = seed.seedAll();
    }

    @Test
    void apiKey_happyPath_persistsPunchesAsProcessed() throws Exception {
        var body = batch("evt-1", PunchEventType.CHECK_IN, Instant.parse("2026-05-28T13:00:00Z"), "RFID-001");
        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicate").value(0));

        var saved = punchEventRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(PunchEventStatus.PROCESSED);
        assertThat(saved.get(0).getEmployeeId()).isEqualTo(data.employeeId());
    }

    @Test
    @WithMockUser(authorities = {"ingestion.write"})
    void jwt_happyPath_acceptsExplicitEmployeeId() throws Exception {
        var body = new PunchIngestionDtos.PunchBatchRequest(
                data.sourceId(),
                List.of(new PunchIngestionDtos.PunchEventRequest(
                        "evt-jwt", PunchEventType.CHECK_IN, Instant.parse("2026-05-28T13:00:00Z"),
                        null, null, data.employeeId(), null)));
        mvc.perform(post("/api/v1/ingestion/punches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));
    }

    @Test
    void perEventIdempotency_secondBatchReportsDuplicate() throws Exception {
        var body = batch("evt-dup", PunchEventType.CHECK_IN, Instant.parse("2026-05-28T13:00:00Z"), "RFID-001");

        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(1))
                .andExpect(jsonPath("$.accepted").value(0));
    }

    @Test
    void batchIdempotencyKey_cachesPriorResponse() throws Exception {
        var body = batch("evt-cache", PunchEventType.CHECK_IN, Instant.parse("2026-05-28T13:00:00Z"), "RFID-001");
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        // Same Idempotency-Key returns the cached response — counters are the original (1 accepted),
        // not duplicates, because the cached response is replayed.
        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        // DB still has only one row (the cache short-circuits before any DB writes).
        assertThat(punchEventRepository.findAll()).hasSize(1);
    }

    @Test
    void unresolvedCredential_persistsAsUnresolvedAndReportsCounter() throws Exception {
        var body = batch("evt-unres", PunchEventType.CHECK_IN, Instant.parse("2026-05-28T13:00:00Z"), "RFID-UNKNOWN");
        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unresolved").value(1))
                .andExpect(jsonPath("$.accepted").value(0));

        var saved = punchEventRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(PunchEventStatus.UNRESOLVED);
        assertThat(saved.get(0).getEmployeeId()).isNull();
    }

    @Test
    void futureEventTimeMarkedInvalid() throws Exception {
        var body = batch("evt-future", PunchEventType.CHECK_IN,
                Instant.now().plus(2, ChronoUnit.HOURS), "RFID-001");
        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invalid").value(1));
    }

    @Test
    void apiKeyCallerCannotIngestForDifferentSource() throws Exception {
        var body = new PunchIngestionDtos.PunchBatchRequest(
                UUID.randomUUID(),  // not the authenticated source
                List.of(new PunchIngestionDtos.PunchEventRequest(
                        "evt-mismatch", PunchEventType.CHECK_IN, Instant.parse("2026-05-28T13:00:00Z"),
                        new PunchIngestionDtos.CredentialRef(CredentialType.RFID, "RFID-001"),
                        null, null, null)));
        mvc.perform(post("/api/v1/ingestion/punches")
                        .header("X-Source-Api-Key", data.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    private PunchIngestionDtos.PunchBatchRequest batch(String externalId, PunchEventType type,
                                                       Instant at, String credentialValue) {
        return new PunchIngestionDtos.PunchBatchRequest(
                data.sourceId(),
                List.of(new PunchIngestionDtos.PunchEventRequest(
                        externalId, type, at,
                        new PunchIngestionDtos.CredentialRef(CredentialType.RFID, credentialValue),
                        null, null, null)));
    }
}

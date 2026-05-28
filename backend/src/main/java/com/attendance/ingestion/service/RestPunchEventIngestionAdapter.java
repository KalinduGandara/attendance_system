package com.attendance.ingestion.service;

import com.attendance.common.error.ApiException;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.ingestion.domain.IngestionStatus;
import com.attendance.ingestion.web.PunchIngestionDtos.EventResult;
import com.attendance.ingestion.web.PunchIngestionDtos.IngestionResponse;
import com.attendance.ingestion.web.PunchIngestionDtos.PunchBatchRequest;
import com.attendance.ingestion.web.PunchIngestionDtos.PunchEventRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * REST adapter implementing {@link PunchEventIngestionPort}. Stateless: defers
 * per-event persistence to {@link PunchEventPersister} (each event gets its own
 * transaction) and per-batch idempotency to {@link PunchBatchIdempotencyService}.
 *
 * <p>Two idempotency layers:
 * <ol>
 *   <li>per-batch via {@code Idempotency-Key} (Caffeine cache returns prior response).</li>
 *   <li>per-event via the DB unique key {@code (ingestion_source_id, external_event_id)};
 *       the persister pre-checks before insert so the happy path never throws.</li>
 * </ol>
 */
@Service
public class RestPunchEventIngestionAdapter implements PunchEventIngestionPort {

    private final IngestionSourceRepository ingestionSourceRepository;
    private final PunchEventPersister persister;
    private final PunchBatchIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public RestPunchEventIngestionAdapter(IngestionSourceRepository ingestionSourceRepository,
                                          PunchEventPersister persister,
                                          PunchBatchIdempotencyService idempotencyService,
                                          ObjectMapper objectMapper) {
        this.ingestionSourceRepository = ingestionSourceRepository;
        this.persister = persister;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public IngestionResponse ingest(UUID authenticatedSourceId,
                                    String idempotencyKey,
                                    PunchBatchRequest batch) {
        if (authenticatedSourceId != null && !authenticatedSourceId.equals(batch.sourceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
                    "Authenticated source does not match the batch's sourceId");
        }
        if (!ingestionSourceRepository.findById(batch.sourceId())
                .map(s -> s.isEnabled()).orElse(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Ingestion source is disabled or does not exist");
        }

        Optional<IngestionResponse> cached = idempotencyService.lookup(batch.sourceId(), idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (batch.events().size() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Batch size exceeds 1000 events");
        }

        Set<String> seenInBatch = new LinkedHashSet<>();
        List<EventResult> results = new ArrayList<>(batch.events().size());
        int accepted = 0, duplicate = 0, unresolved = 0, invalid = 0;

        for (PunchEventRequest req : batch.events()) {
            EventResult result = ingestOne(batch.sourceId(), req, seenInBatch);
            results.add(result);
            switch (IngestionStatus.valueOf(result.status())) {
                case ACCEPTED -> accepted++;
                case DUPLICATE -> duplicate++;
                case UNRESOLVED_CREDENTIAL -> unresolved++;
                case INVALID -> invalid++;
            }
        }

        IngestionResponse response = new IngestionResponse(accepted, duplicate, unresolved, invalid, results);
        idempotencyService.store(batch.sourceId(), idempotencyKey, response);
        return response;
    }

    private EventResult ingestOne(UUID sourceId, PunchEventRequest req, Set<String> seenInBatch) {
        if (req.externalEventId() == null || req.externalEventId().isBlank()) {
            return invalid(req, "externalEventId is required");
        }
        if (req.eventTime() == null) {
            return invalid(req, "eventTime is required");
        }
        if (req.eventTime().isAfter(Instant.now().plusSeconds(300))) {
            return invalid(req, "eventTime is in the future");
        }
        if (!seenInBatch.add(req.externalEventId())) {
            return new EventResult(req.externalEventId(), IngestionStatus.DUPLICATE.name(),
                    null, "duplicate externalEventId within batch");
        }
        return persister.persist(
                sourceId,
                req.externalEventId(),
                req.eventType(),
                req.eventTime(),
                req.credential() == null ? null : req.credential().type(),
                req.credential() == null ? null : req.credential().value(),
                req.employeeId(),
                req.deviceId(),
                serializeRaw(req));
    }

    private String serializeRaw(PunchEventRequest req) {
        if (req.rawPayload() == null || req.rawPayload().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(req.rawPayload());
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static EventResult invalid(PunchEventRequest req, String detail) {
        return new EventResult(req == null ? null : req.externalEventId(),
                IngestionStatus.INVALID.name(), null, detail);
    }
}

package com.attendance.timecard.service;

import com.attendance.common.audit.RequestContext;
import com.attendance.common.error.ApiException;
import com.attendance.device.domain.IngestionSource;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.domain.TimeCardEdit;
import com.attendance.timecard.domain.TimeCardEditChangeType;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.repository.TimeCardEditRepository;
import com.attendance.timecard.web.TimeCardDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a manual edit to a time card. Edits to punches are immutable: the
 * affected {@link PunchEvent} is marked {@code SUPERSEDED} and a fresh row is
 * persisted in its place (see api-contracts.md §4.3). Each edit is recorded
 * in {@code time_card_edit} with the before/after JSON for audit, and the
 * affected day is recomputed synchronously so the response reflects the new
 * state.
 */
@Service
public class TimeCardEditService {

    private final DailyTimeCardRepository cardRepository;
    private final PunchEventRepository punchRepository;
    private final TimeCardEditRepository editRepository;
    private final IngestionSourceRepository sourceRepository;
    private final TimeCardRecomputeService recomputeService;
    private final TimeCardReadService readService;
    private final ObjectMapper objectMapper;

    public TimeCardEditService(DailyTimeCardRepository cardRepository,
                               PunchEventRepository punchRepository,
                               TimeCardEditRepository editRepository,
                               IngestionSourceRepository sourceRepository,
                               TimeCardRecomputeService recomputeService,
                               TimeCardReadService readService,
                               ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.punchRepository = punchRepository;
        this.editRepository = editRepository;
        this.sourceRepository = sourceRepository;
        this.recomputeService = recomputeService;
        this.readService = readService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TimeCardDtos.TimeCardDetailResponse applyEdit(UUID timeCardId, TimeCardDtos.EditRequest req) {
        DailyTimeCard card = cardRepository.findById(timeCardId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Time card not found"));
        UUID actorUserId = RequestContext.actorUserId().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized",
                        "Manual edits require an authenticated user"));

        switch (req.changeType()) {
            case PUNCH_ADD -> applyPunchAdd(card, req, actorUserId);
            case PUNCH_EDIT -> applyPunchEdit(card, req, actorUserId);
            case PUNCH_DELETE -> applyPunchDelete(card, req, actorUserId);
            case STATUS_CHANGE -> applyStatusChange(card, req, actorUserId);
            case NOTE -> applyNote(card, req, actorUserId);
        }

        DailyTimeCard recomputed = recomputeService.recompute(card.getEmployeeId(), card.getWorkDate());
        return readService.toDetail(recomputed);
    }

    private void applyPunchAdd(DailyTimeCard card, TimeCardDtos.EditRequest req, UUID actorUserId) {
        if (req.eventType() == null || req.newEventTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "PUNCH_ADD requires eventType and newEventTime");
        }
        UUID sourceId = resolveSourceId(req.ingestionSourceId());
        String externalId = "manual:" + UUID.randomUUID();

        PunchEvent created = createPunch(card.getEmployeeId(), sourceId, externalId,
                req.eventType(), req.newEventTime());

        writeEdit(card, created.getId(), TimeCardEditChangeType.PUNCH_ADD,
                null, snapshotPunch(created), req.reason(), actorUserId);
    }

    private void applyPunchEdit(DailyTimeCard card, TimeCardDtos.EditRequest req, UUID actorUserId) {
        if (req.punchEventId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "PUNCH_EDIT requires punchEventId");
        }
        PunchEvent original = loadPunch(req.punchEventId(), card.getEmployeeId());
        String before = snapshotPunch(original);

        Instant newTime = req.newEventTime() != null ? req.newEventTime() : original.getEventTimeUtc();
        PunchEventType newType = req.eventType() != null ? req.eventType() : original.getEventType();

        original.setStatus(PunchEventStatus.SUPERSEDED);
        punchRepository.saveAndFlush(original);

        UUID sourceId = req.ingestionSourceId() != null
                ? req.ingestionSourceId()
                : original.getIngestionSourceId();
        String externalId = "manual:edit:" + original.getId() + ":" + UUID.randomUUID();

        PunchEvent replacement = createPunch(card.getEmployeeId(), sourceId, externalId, newType, newTime);

        writeEdit(card, replacement.getId(), TimeCardEditChangeType.PUNCH_EDIT,
                before, snapshotPunch(replacement), req.reason(), actorUserId);
    }

    private void applyPunchDelete(DailyTimeCard card, TimeCardDtos.EditRequest req, UUID actorUserId) {
        if (req.punchEventId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "PUNCH_DELETE requires punchEventId");
        }
        PunchEvent original = loadPunch(req.punchEventId(), card.getEmployeeId());
        String before = snapshotPunch(original);
        original.setStatus(PunchEventStatus.SUPERSEDED);
        punchRepository.saveAndFlush(original);

        writeEdit(card, original.getId(), TimeCardEditChangeType.PUNCH_DELETE,
                before, null, req.reason(), actorUserId);
    }

    private void applyStatusChange(DailyTimeCard card, TimeCardDtos.EditRequest req, UUID actorUserId) {
        if (req.newStatus() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "STATUS_CHANGE requires newStatus");
        }
        DailyTimeCardStatus prev = card.getStatus();
        card.setStatus(req.newStatus());
        cardRepository.saveAndFlush(card);

        writeEdit(card, null, TimeCardEditChangeType.STATUS_CHANGE,
                jsonStatus(prev), jsonStatus(req.newStatus()), req.reason(), actorUserId);
    }

    private void applyNote(DailyTimeCard card, TimeCardDtos.EditRequest req, UUID actorUserId) {
        String prev = card.getNotes();
        card.setNotes(req.newNotes());
        cardRepository.saveAndFlush(card);

        writeEdit(card, null, TimeCardEditChangeType.NOTE,
                jsonNote(prev), jsonNote(req.newNotes()), req.reason(), actorUserId);
    }

    private PunchEvent loadPunch(UUID punchId, UUID employeeId) {
        PunchEvent p = punchRepository.findById(punchId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Punch event not found"));
        if (!employeeId.equals(p.getEmployeeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Punch event does not belong to this time card's employee");
        }
        if (p.getStatus() == PunchEventStatus.SUPERSEDED) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Punch event has already been superseded");
        }
        return p;
    }

    private PunchEvent createPunch(UUID employeeId, UUID sourceId, String externalId,
                                   PunchEventType type, Instant at) {
        PunchEvent e = new PunchEvent();
        e.setEmployeeId(employeeId);
        e.setIngestionSourceId(sourceId);
        e.setExternalEventId(externalId);
        e.setEventType(type);
        e.setEventTimeUtc(at);
        e.setStatus(PunchEventStatus.PROCESSED);
        e.setProcessedAt(Instant.now());
        return punchRepository.saveAndFlush(e);
    }

    private UUID resolveSourceId(UUID requested) {
        if (requested != null) {
            if (sourceRepository.findById(requested).isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                        "Unknown ingestion source");
            }
            return requested;
        }
        return sourceRepository.findAllByOrderByNameAsc().stream()
                .findFirst()
                .map(IngestionSource::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "validation",
                        "No ingestion source configured for manual edits"));
    }

    private void writeEdit(DailyTimeCard card, UUID punchEventId, TimeCardEditChangeType type,
                           String before, String after, String reason, UUID actorUserId) {
        TimeCardEdit edit = new TimeCardEdit();
        edit.setDailyTimeCardId(card.getId());
        edit.setPunchEventId(punchEventId);
        edit.setChangeType(type);
        edit.setBeforeJson(before);
        edit.setAfterJson(after);
        edit.setReason(reason);
        edit.setEditedByUserId(actorUserId);
        edit.setEditedAt(Instant.now());
        editRepository.saveAndFlush(edit);
    }

    private String snapshotPunch(PunchEvent p) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", p.getId().toString());
        n.put("eventType", p.getEventType().name());
        n.put("eventTimeUtc", p.getEventTimeUtc().toString());
        n.put("status", p.getStatus().name());
        n.put("ingestionSourceId", p.getIngestionSourceId().toString());
        n.put("externalEventId", p.getExternalEventId());
        try {
            return objectMapper.writeValueAsString(n);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                    "Failed to serialize punch snapshot");
        }
    }

    private String jsonStatus(DailyTimeCardStatus status) {
        ObjectNode n = objectMapper.createObjectNode();
        if (status != null) {
            n.put("status", status.name());
        } else {
            n.putNull("status");
        }
        try {
            return objectMapper.writeValueAsString(n);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                    "Failed to serialize status snapshot");
        }
    }

    private String jsonNote(String note) {
        ObjectNode n = objectMapper.createObjectNode();
        if (note != null) {
            n.put("notes", note);
        } else {
            n.putNull("notes");
        }
        try {
            return objectMapper.writeValueAsString(n);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                    "Failed to serialize note snapshot");
        }
    }
}

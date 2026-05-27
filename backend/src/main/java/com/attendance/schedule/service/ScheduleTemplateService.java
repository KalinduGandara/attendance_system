package com.attendance.schedule.service;

import com.attendance.common.error.ApiException;
import com.attendance.platform.persistence.CacheConfig;
import com.attendance.schedule.domain.ScheduleTemplate;
import com.attendance.schedule.domain.ScheduleTemplateDay;
import com.attendance.schedule.repository.ScheduleAssignmentRepository;
import com.attendance.schedule.repository.ScheduleTemplateRepository;
import com.attendance.schedule.web.ScheduleDtos;
import com.attendance.shift.repository.ShiftRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository repository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final ShiftRepository shiftRepository;

    public ScheduleTemplateService(ScheduleTemplateRepository repository,
                                   ScheduleAssignmentRepository assignmentRepository,
                                   ShiftRepository shiftRepository) {
        this.repository = repository;
        this.assignmentRepository = assignmentRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.TemplateResponse> list(String q) {
        return repository.search(blankToNull(q)).stream()
                .map(ScheduleTemplateService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDtos.TemplateResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATES, allEntries = true)
    public ScheduleDtos.TemplateResponse create(ScheduleDtos.TemplateRequest req) {
        validate(req, null);
        ScheduleTemplate t = new ScheduleTemplate();
        apply(t, req);
        return toResponse(repository.saveAndFlush(t));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATES, allEntries = true)
    public ScheduleDtos.TemplateResponse update(UUID id, ScheduleDtos.TemplateRequest req) {
        ScheduleTemplate t = findOrThrow(id);
        if (req.expectedVersion() != null && !req.expectedVersion().equals(t.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Schedule template was modified by another request; reload and retry");
        }
        validate(req, id);
        apply(t, req);
        return toResponse(repository.saveAndFlush(t));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SCHEDULE_TEMPLATES, allEntries = true)
    public void delete(UUID id) {
        ScheduleTemplate t = findOrThrow(id);
        if (assignmentRepository.countByTemplateId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Template is referenced by an assignment; remove assignments first");
        }
        repository.delete(t);
    }

    private void validate(ScheduleDtos.TemplateRequest req, UUID selfId) {
        Optional<ScheduleTemplate> existing = repository.findByName(req.name().trim());
        if (existing.isPresent() && (selfId == null || !existing.get().getId().equals(selfId))) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "A template with this name already exists");
        }
        List<ScheduleDtos.TemplateDayRequest> days = req.days() == null ? List.of() : req.days();
        Set<Integer> seen = new HashSet<>();
        for (ScheduleDtos.TemplateDayRequest d : days) {
            if (d.dayIndex() >= req.cycleLengthDays()) {
                throw badRequest("day index " + d.dayIndex() + " is out of range for cycle of "
                        + req.cycleLengthDays() + " days");
            }
            if (!seen.add(d.dayIndex())) {
                throw badRequest("duplicate day index " + d.dayIndex());
            }
            if (d.shiftId() != null && !shiftRepository.existsById(d.shiftId())) {
                throw badRequest("day " + d.dayIndex() + " references unknown shift");
            }
        }
    }

    private void apply(ScheduleTemplate t, ScheduleDtos.TemplateRequest req) {
        t.setName(req.name().trim());
        t.setCycleType(req.cycleType());
        t.setCycleLengthDays(req.cycleLengthDays());
        t.setDescription(blankToNull(req.description()));
        t.getDays().clear();
        if (req.days() != null) {
            for (ScheduleDtos.TemplateDayRequest d : req.days()) {
                ScheduleTemplateDay row = new ScheduleTemplateDay();
                row.setTemplate(t);
                row.setDayIndex(d.dayIndex());
                row.setShiftId(d.shiftId());
                t.getDays().add(row);
            }
        }
    }

    private ScheduleTemplate findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Schedule template not found"));
    }

    static ScheduleDtos.TemplateResponse toResponse(ScheduleTemplate t) {
        return new ScheduleDtos.TemplateResponse(
                t.getId(), t.getName(), t.getCycleType(), t.getCycleLengthDays(),
                t.getDescription(),
                t.getDays().stream()
                        .map(d -> new ScheduleDtos.TemplateDayResponse(
                                d.getId(), d.getDayIndex(), d.getShiftId()))
                        .toList(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
    }

    private static ApiException badRequest(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation", msg);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

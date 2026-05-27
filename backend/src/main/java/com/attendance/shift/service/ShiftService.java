package com.attendance.shift.service;

import com.attendance.common.error.ApiException;
import com.attendance.platform.persistence.CacheConfig;
import com.attendance.shift.domain.BreakRule;
import com.attendance.shift.domain.OvertimeRule;
import com.attendance.shift.domain.Shift;
import com.attendance.shift.domain.ShiftGraceRule;
import com.attendance.shift.domain.ShiftRoundingRule;
import com.attendance.shift.domain.ShiftSegment;
import com.attendance.shift.domain.ShiftType;
import com.attendance.shift.repository.ShiftRepository;
import com.attendance.shift.web.ShiftDtos;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ShiftService {

    private final ShiftRepository repository;
    private final TimeCodeRepository timeCodeRepository;

    public ShiftService(ShiftRepository repository, TimeCodeRepository timeCodeRepository) {
        this.repository = repository;
        this.timeCodeRepository = timeCodeRepository;
    }

    @Transactional(readOnly = true)
    public List<ShiftDtos.ShiftResponse> list(String q, ShiftType type, Boolean active) {
        return repository.search(blankToNull(q), type, active).stream()
                .map(ShiftService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftDtos.ShiftResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Cache by id; the time-card engine (Phase 5) reads shifts on its hot
     * path. Cache key is the id; eviction is all-entries on any mutation
     * since shifts are shared across many time cards.
     */
    @Cacheable(value = CacheConfig.SHIFTS, key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<Shift> findCached(UUID id) {
        return repository.findById(id);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SHIFTS, allEntries = true)
    public ShiftDtos.ShiftResponse create(ShiftDtos.ShiftRequest req) {
        validate(req);
        Shift s = new Shift();
        apply(s, req);
        return toResponse(repository.saveAndFlush(s));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SHIFTS, allEntries = true)
    public ShiftDtos.ShiftResponse update(UUID id, ShiftDtos.ShiftRequest req) {
        Shift s = findOrThrow(id);
        if (req.expectedVersion() != null && !req.expectedVersion().equals(s.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Shift was modified by another request; reload and retry");
        }
        validate(req);
        apply(s, req);
        return toResponse(repository.saveAndFlush(s));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SHIFTS, allEntries = true)
    public void delete(UUID id) {
        Shift s = findOrThrow(id);
        if (repository.countAsFloatingCandidate(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Shift is referenced as a floating candidate; remove the reference first");
        }
        try {
            repository.delete(s);
            repository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Shift is referenced by schedules or time cards; deactivate instead");
        }
    }

    // ----- validation -----

    private void validate(ShiftDtos.ShiftRequest req) {
        TimeCode attendance = timeCodeRepository.findById(req.attendanceTimeCodeId())
                .orElseThrow(() -> badRequest("attendanceTimeCodeId references unknown time code"));
        if (attendance.getCategory() == TimeCodeCategory.LEAVE) {
            throw badRequest("attendanceTimeCodeId must be ATTENDANCE or OVERTIME category");
        }

        List<ShiftDtos.SegmentRequest> segments =
                req.segments() == null ? List.of() : req.segments();
        for (ShiftDtos.SegmentRequest seg : segments) {
            if (seg.endMinuteOfDay() <= seg.startMinuteOfDay()) {
                throw badRequest("segment end must be after start");
            }
        }
        Set<Integer> segmentOrders = new HashSet<>();
        for (ShiftDtos.SegmentRequest seg : segments) {
            if (!segmentOrders.add(seg.segmentOrder())) {
                throw badRequest("duplicate segment order " + seg.segmentOrder());
            }
        }

        if (req.shiftType() == ShiftType.FIXED && segments.isEmpty()) {
            throw badRequest("FIXED shift requires at least one segment");
        }
        if (req.shiftType() == ShiftType.FLEXIBLE) {
            if (segments.isEmpty()) {
                throw badRequest("FLEXIBLE shift requires at least one segment with required minutes");
            }
            for (ShiftDtos.SegmentRequest seg : segments) {
                if (seg.requiredMinutes() == null || seg.requiredMinutes() <= 0) {
                    throw badRequest("FLEXIBLE segments must have positive requiredMinutes");
                }
                int window = seg.endMinuteOfDay() - seg.startMinuteOfDay();
                if (seg.requiredMinutes() > window) {
                    throw badRequest("FLEXIBLE segment requiredMinutes exceeds its window");
                }
            }
        }
        if (req.shiftType() == ShiftType.FLOATING) {
            Set<UUID> candidates = req.candidateShiftIds() == null
                    ? Set.of() : req.candidateShiftIds();
            if (candidates.isEmpty()) {
                throw badRequest("FLOATING shift requires at least one candidate");
            }
            for (UUID cid : candidates) {
                Shift candidate = repository.findById(cid)
                        .orElseThrow(() -> badRequest("candidate shift " + cid + " not found"));
                if (candidate.getShiftType() == ShiftType.FLOATING) {
                    throw badRequest("FLOATING candidates must be FIXED or FLEXIBLE");
                }
            }
        } else if (req.candidateShiftIds() != null && !req.candidateShiftIds().isEmpty()) {
            throw badRequest("candidateShiftIds is only valid for FLOATING shifts");
        }

        // overtime tiers: strictly increasing after_minutes_worked along sequence_order
        List<ShiftDtos.OvertimeRuleRequest> ot =
                req.overtimeRules() == null ? List.of() : req.overtimeRules();
        Set<Integer> seqSeen = new HashSet<>();
        for (ShiftDtos.OvertimeRuleRequest tier : ot) {
            if (!seqSeen.add(tier.sequenceOrder())) {
                throw badRequest("duplicate overtime sequenceOrder " + tier.sequenceOrder());
            }
        }
        List<ShiftDtos.OvertimeRuleRequest> sorted = ot.stream()
                .sorted((a, b) -> Integer.compare(a.sequenceOrder(), b.sequenceOrder()))
                .toList();
        Integer prev = null;
        for (ShiftDtos.OvertimeRuleRequest tier : sorted) {
            if (prev != null && tier.afterMinutesWorked() <= prev) {
                throw badRequest("overtime afterMinutesWorked must be strictly increasing");
            }
            prev = tier.afterMinutesWorked();
            TimeCode tc = timeCodeRepository.findById(tier.timeCodeId())
                    .orElseThrow(() -> badRequest("overtime tier references unknown time code"));
            if (tc.getCategory() != TimeCodeCategory.OVERTIME) {
                throw badRequest("overtime tier time code must have category OVERTIME");
            }
        }

        // break rules: every paid tracked break needs a time code
        for (ShiftDtos.BreakRuleRequest br : safe(req.breakRules())) {
            if (br.paid() && br.timeCodeId() == null) {
                throw badRequest("paid break requires a time code");
            }
            if (br.timeCodeId() != null && timeCodeRepository.findById(br.timeCodeId()).isEmpty()) {
                throw badRequest("break references unknown time code");
            }
        }
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ----- mapping -----

    private void apply(Shift s, ShiftDtos.ShiftRequest req) {
        s.setName(req.name().trim());
        s.setShiftType(req.shiftType());
        s.setColor(req.color());
        s.setTimezone(blankToNull(req.timezone()));
        s.setDescription(blankToNull(req.description()));
        s.setActive(req.active() == null ? true : req.active());
        s.setAttendanceTimeCodeId(req.attendanceTimeCodeId());

        s.getSegments().clear();
        for (ShiftDtos.SegmentRequest seg : safe(req.segments())) {
            ShiftSegment row = new ShiftSegment();
            row.setShift(s);
            row.setSegmentOrder(seg.segmentOrder());
            row.setStartMinuteOfDay(seg.startMinuteOfDay());
            row.setEndMinuteOfDay(seg.endMinuteOfDay());
            row.setRequiredMinutes(seg.requiredMinutes());
            s.getSegments().add(row);
        }

        s.getRoundingRules().clear();
        for (ShiftDtos.RoundingRuleRequest r : safe(req.roundingRules())) {
            ShiftRoundingRule row = new ShiftRoundingRule();
            row.setShift(s);
            row.setKind(r.kind());
            row.setUnitMinutes(r.unitMinutes());
            row.setMode(r.mode());
            s.getRoundingRules().add(row);
        }

        s.getGraceRules().clear();
        for (ShiftDtos.GraceRuleRequest g : safe(req.graceRules())) {
            ShiftGraceRule row = new ShiftGraceRule();
            row.setShift(s);
            row.setKind(g.kind());
            row.setMinutes(g.minutes());
            s.getGraceRules().add(row);
        }

        s.getBreakRules().clear();
        for (ShiftDtos.BreakRuleRequest b : safe(req.breakRules())) {
            BreakRule row = new BreakRule();
            row.setShift(s);
            row.setName(b.name().trim());
            row.setKind(b.kind());
            row.setDurationMinutes(b.durationMinutes());
            row.setEarliestStartMinute(b.earliestStartMinute());
            row.setAfterHoursWorked(b.afterHoursWorked());
            row.setPaid(Boolean.TRUE.equals(b.paid()));
            row.setTimeCodeId(b.timeCodeId());
            s.getBreakRules().add(row);
        }

        s.getOvertimeRules().clear();
        for (ShiftDtos.OvertimeRuleRequest o : safe(req.overtimeRules())) {
            OvertimeRule row = new OvertimeRule();
            row.setShift(s);
            row.setSequenceOrder(o.sequenceOrder());
            row.setAfterMinutesWorked(o.afterMinutesWorked());
            row.setTimeCodeId(o.timeCodeId());
            row.setMaxMinutes(o.maxMinutes());
            s.getOvertimeRules().add(row);
        }

        s.getCandidateShiftIds().clear();
        if (req.candidateShiftIds() != null) {
            s.getCandidateShiftIds().addAll(req.candidateShiftIds());
        }
    }

    static ShiftDtos.ShiftResponse toResponse(Shift s) {
        return new ShiftDtos.ShiftResponse(
                s.getId(), s.getName(), s.getShiftType(), s.getColor(),
                s.getTimezone(), s.getDescription(), s.isActive(), s.getAttendanceTimeCodeId(),
                s.getSegments().stream()
                        .map(seg -> new ShiftDtos.SegmentResponse(
                                seg.getId(), seg.getSegmentOrder(),
                                seg.getStartMinuteOfDay(), seg.getEndMinuteOfDay(),
                                seg.getRequiredMinutes()))
                        .toList(),
                s.getRoundingRules().stream()
                        .map(r -> new ShiftDtos.RoundingRuleResponse(
                                r.getId(), r.getKind(), r.getUnitMinutes(), r.getMode()))
                        .toList(),
                s.getGraceRules().stream()
                        .map(g -> new ShiftDtos.GraceRuleResponse(
                                g.getId(), g.getKind(), g.getMinutes()))
                        .toList(),
                s.getBreakRules().stream()
                        .map(b -> new ShiftDtos.BreakRuleResponse(
                                b.getId(), b.getName(), b.getKind(), b.getDurationMinutes(),
                                b.getEarliestStartMinute(), b.getAfterHoursWorked(),
                                b.isPaid(), b.getTimeCodeId()))
                        .toList(),
                s.getOvertimeRules().stream()
                        .map(o -> new ShiftDtos.OvertimeRuleResponse(
                                o.getId(), o.getSequenceOrder(), o.getAfterMinutesWorked(),
                                o.getTimeCodeId(), o.getMaxMinutes()))
                        .toList(),
                Set.copyOf(s.getCandidateShiftIds()),
                s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }

    private Shift findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Shift not found"));
    }

    private static ApiException badRequest(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation", msg);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

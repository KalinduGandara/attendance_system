package com.attendance.timecard.service;

import com.attendance.common.error.ApiException;
import com.attendance.exception.repository.ExceptionEventRepository;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.shift.service.ShiftService;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.TimeCardEdit;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.repository.TimeCardEditRepository;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.repository.TimeCodeRepository;
import com.attendance.timecard.web.TimeCardDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TimeCardReadService {

    private final DailyTimeCardRepository repository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final TimeCodeRepository timeCodeRepository;
    private final ShiftService shiftService;
    private final ExceptionEventRepository exceptionRepository;
    private final PunchEventRepository punchEventRepository;
    private final TimeCardEditRepository editRepository;

    public TimeCardReadService(DailyTimeCardRepository repository,
                               EmployeeRepository employeeRepository,
                               EmployeeService employeeService,
                               TimeCodeRepository timeCodeRepository,
                               ShiftService shiftService,
                               ExceptionEventRepository exceptionRepository,
                               PunchEventRepository punchEventRepository,
                               TimeCardEditRepository editRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.timeCodeRepository = timeCodeRepository;
        this.shiftService = shiftService;
        this.exceptionRepository = exceptionRepository;
        this.punchEventRepository = punchEventRepository;
        this.editRepository = editRepository;
    }

    @Transactional(readOnly = true)
    public List<TimeCardDtos.TimeCardResponse> list(UUID employeeId,
                                                   DailyTimeCardStatus status,
                                                   LocalDate from,
                                                   LocalDate to) {
        List<DailyTimeCard> cards = repository.search(employeeId, status, from, to);
        Map<UUID, Employee> employees = loadEmployees(cards);
        Map<UUID, TimeCode> timeCodes = loadTimeCodes(cards);
        return cards.stream()
                .map(c -> toSummary(c, employees.get(c.getEmployeeId()), timeCodes))
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeCardDtos.TimeCardResponse get(UUID id) {
        DailyTimeCard card = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Time card not found"));
        Employee employee = employeeRepository.findById(card.getEmployeeId()).orElse(null);
        Map<UUID, TimeCode> timeCodes = loadTimeCodes(List.of(card));
        return toSummary(card, employee, timeCodes);
    }

    @Transactional(readOnly = true)
    public TimeCardDtos.TimeCardDetailResponse getDetail(UUID id) {
        DailyTimeCard card = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Time card not found"));
        return toDetail(card);
    }

    /**
     * Used internally (e.g. by the edit endpoint after recompute) to build the
     * detail view directly from an already-loaded entity, avoiding a second
     * lookup.
     */
    @Transactional(readOnly = true)
    public TimeCardDtos.TimeCardDetailResponse toDetail(DailyTimeCard card) {
        Employee employee = employeeRepository.findById(card.getEmployeeId()).orElse(null);
        Map<UUID, TimeCode> timeCodes = loadTimeCodes(List.of(card));
        TimeCardDtos.TimeCardResponse summary = toSummary(card, employee, timeCodes);

        List<PunchEvent> punches = loadPunchesForDay(card.getEmployeeId(), card.getWorkDate());
        List<TimeCardEdit> edits = editRepository.findByDailyTimeCardIdOrderByEditedAtAsc(card.getId());

        return new TimeCardDtos.TimeCardDetailResponse(
                summary.id(),
                summary.employee(),
                summary.workDate(),
                summary.status(),
                summary.resolvedShift(),
                summary.scheduledStart(),
                summary.scheduledEnd(),
                summary.actualStart(),
                summary.actualEnd(),
                summary.workedMinutes(),
                summary.breakMinutes(),
                summary.overtimeMinutes(),
                summary.lateMinutes(),
                summary.earlyOutMinutes(),
                card.getNotes(),
                summary.breakdown(),
                summary.exceptions(),
                punches.stream().map(TimeCardReadService::toPunchResponse).toList(),
                edits.stream().map(TimeCardReadService::toEditDto).toList(),
                summary.computedAt(),
                summary.version());
    }

    private List<PunchEvent> loadPunchesForDay(UUID employeeId, LocalDate workDate) {
        ZoneId zone = ZoneId.of(employeeService.timezoneForEmployee(employeeId));
        Instant dayStart = workDate.atStartOfDay(zone).toInstant();
        Instant from = dayStart.minus(Duration.ofHours(12));
        Instant to = dayStart.plus(Duration.ofHours(36));
        return punchEventRepository.findProcessedForEmployeeBetween(employeeId, from, to).stream()
                .filter(p -> ZonedDateTime.ofInstant(p.getEventTimeUtc(), zone)
                        .toLocalDate().equals(workDate))
                .toList();
    }

    private Map<UUID, Employee> loadEmployees(List<DailyTimeCard> cards) {
        Set<UUID> ids = cards.stream().map(DailyTimeCard::getEmployeeId).collect(Collectors.toSet());
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
    }

    private Map<UUID, TimeCode> loadTimeCodes(List<DailyTimeCard> cards) {
        Set<UUID> ids = cards.stream()
                .flatMap(c -> c.getBreakdowns().stream())
                .map(b -> b.getTimeCodeId())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return timeCodeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TimeCode::getId, t -> t));
    }

    private TimeCardDtos.TimeCardResponse toSummary(DailyTimeCard card,
                                                    Employee employee,
                                                    Map<UUID, TimeCode> timeCodes) {
        TimeCardDtos.EmployeeRef empRef = employee == null
                ? null
                : new TimeCardDtos.EmployeeRef(employee.getId(), employee.getEmployeeCode(),
                        employee.getFirstName() + " " + employee.getLastName());

        TimeCardDtos.ShiftRef shiftRef = null;
        if (card.getResolvedShiftId() != null) {
            shiftRef = shiftService.findCached(card.getResolvedShiftId())
                    .map(s -> new TimeCardDtos.ShiftRef(s.getId(), s.getName(), s.getColor()))
                    .orElse(null);
        }

        List<TimeCardDtos.BreakdownDto> breakdowns = card.getBreakdowns().stream()
                .map(b -> {
                    TimeCode tc = timeCodes.get(b.getTimeCodeId());
                    return new TimeCardDtos.BreakdownDto(
                            tc == null ? null : tc.getCode(),
                            b.getTimeCodeId(),
                            b.getMinutes(),
                            b.getRatedMinutes(),
                            b.getSequenceOrder());
                })
                .toList();

        List<TimeCardDtos.ExceptionRef> exceptions = exceptionRepository
                .findByEmployeeIdAndWorkDate(card.getEmployeeId(), card.getWorkDate()).stream()
                .map(e -> new TimeCardDtos.ExceptionRef(
                        e.getId(),
                        e.getExceptionType().name(),
                        e.getSeverity().name(),
                        e.getStatus().name()))
                .toList();

        return new TimeCardDtos.TimeCardResponse(
                card.getId(),
                empRef,
                card.getWorkDate(),
                card.getStatus(),
                shiftRef,
                card.getScheduledStartUtc(),
                card.getScheduledEndUtc(),
                card.getActualStartUtc(),
                card.getActualEndUtc(),
                card.getWorkedMinutes(),
                card.getBreakMinutes(),
                card.getOvertimeMinutes(),
                card.getLateMinutes(),
                card.getEarlyOutMinutes(),
                breakdowns,
                exceptions,
                card.getComputedAt(),
                card.getVersion());
    }

    private static TimeCardDtos.PunchEventResponse toPunchResponse(PunchEvent p) {
        return new TimeCardDtos.PunchEventResponse(
                p.getId(),
                p.getEmployeeId(),
                p.getDeviceId(),
                p.getIngestionSourceId(),
                p.getExternalEventId(),
                p.getEventType(),
                p.getEventTimeUtc(),
                p.getStatus(),
                p.getProcessedAt());
    }

    private static TimeCardDtos.TimeCardEditDto toEditDto(TimeCardEdit e) {
        return new TimeCardDtos.TimeCardEditDto(
                e.getId(),
                e.getPunchEventId(),
                e.getChangeType(),
                e.getBeforeJson(),
                e.getAfterJson(),
                e.getReason(),
                e.getEditedByUserId(),
                e.getEditedAt());
    }
}

package com.attendance.timecard.service;

import com.attendance.common.error.ApiException;
import com.attendance.exception.repository.ExceptionEventRepository;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.shift.service.ShiftService;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.repository.TimeCodeRepository;
import com.attendance.timecard.web.TimeCardDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TimeCardReadService {

    private final DailyTimeCardRepository repository;
    private final EmployeeRepository employeeRepository;
    private final TimeCodeRepository timeCodeRepository;
    private final ShiftService shiftService;
    private final ExceptionEventRepository exceptionRepository;

    public TimeCardReadService(DailyTimeCardRepository repository,
                               EmployeeRepository employeeRepository,
                               TimeCodeRepository timeCodeRepository,
                               ShiftService shiftService,
                               ExceptionEventRepository exceptionRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.timeCodeRepository = timeCodeRepository;
        this.shiftService = shiftService;
        this.exceptionRepository = exceptionRepository;
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
                .map(c -> toResponse(c, employees.get(c.getEmployeeId()), timeCodes))
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeCardDtos.TimeCardResponse get(UUID id) {
        DailyTimeCard card = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Time card not found"));
        Employee employee = employeeRepository.findById(card.getEmployeeId()).orElse(null);
        Map<UUID, TimeCode> timeCodes = loadTimeCodes(List.of(card));
        return toResponse(card, employee, timeCodes);
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

    private TimeCardDtos.TimeCardResponse toResponse(DailyTimeCard card,
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
}

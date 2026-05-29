package com.attendance.timecard.service;

import com.attendance.exception.service.ExceptionEventService;
import com.attendance.exception.service.ExceptionEventService.EmittedException;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.organization.repository.HolidayRepository;
import com.attendance.organization.service.EmployeeService;
import com.attendance.schedule.service.ResolvedSchedule;
import com.attendance.schedule.service.ScheduleResolver;
import com.attendance.shift.domain.Shift;
import com.attendance.shift.domain.ShiftType;
import com.attendance.shift.service.ShiftService;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.TimeCardBreakdown;
import com.attendance.timecard.engine.EngineInputs;
import com.attendance.timecard.engine.EngineOutput;
import com.attendance.timecard.engine.FloatingShiftSelector;
import com.attendance.timecard.engine.TimeCardCalculator;
import com.attendance.timecard.engine.snapshots.LeaveSnapshot;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a recompute for an (employee, work_date) pair: loads all engine
 * inputs, runs {@link TimeCardCalculator#compute(EngineInputs)}, then upserts
 * the resulting time card, breakdown, and exception rows in one transaction.
 *
 * <p>The engine itself stays pure; this class is the impure boundary.
 */
@Service
public class TimeCardRecomputeService {

    private final ScheduleResolver scheduleResolver;
    private final ShiftService shiftService;
    private final TimeCodeRepository timeCodeRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeService employeeService;
    private final PunchEventRepository punchEventRepository;
    private final DailyTimeCardRepository dailyTimeCardRepository;
    private final ExceptionEventService exceptionEventService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public TimeCardRecomputeService(ScheduleResolver scheduleResolver,
                                    ShiftService shiftService,
                                    TimeCodeRepository timeCodeRepository,
                                    HolidayRepository holidayRepository,
                                    EmployeeService employeeService,
                                    PunchEventRepository punchEventRepository,
                                    DailyTimeCardRepository dailyTimeCardRepository,
                                    ExceptionEventService exceptionEventService,
                                    LeaveRequestRepository leaveRequestRepository,
                                    LeaveTypeRepository leaveTypeRepository) {
        this.scheduleResolver = scheduleResolver;
        this.shiftService = shiftService;
        this.timeCodeRepository = timeCodeRepository;
        this.holidayRepository = holidayRepository;
        this.employeeService = employeeService;
        this.punchEventRepository = punchEventRepository;
        this.dailyTimeCardRepository = dailyTimeCardRepository;
        this.exceptionEventService = exceptionEventService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Transactional
    public DailyTimeCard recompute(UUID employeeId, LocalDate workDate) {
        ZoneId zone = ZoneId.of(employeeService.timezoneForEmployee(employeeId));

        // Window for relevant punches: the calendar day in the employee's tz,
        // ± 12h to capture shifts that cross midnight.
        Instant dayStart = workDate.atStartOfDay(zone).toInstant();
        Instant from = dayStart.minus(java.time.Duration.ofHours(12));
        Instant to = dayStart.plus(java.time.Duration.ofHours(36));

        List<PunchEvent> punchEntities = punchEventRepository.findProcessedForEmployeeBetween(
                employeeId, from, to);
        // Keep only punches whose work-date (employee local) matches.
        List<PunchEvent> dayPunches = punchEntities.stream()
                .filter(p -> ZonedDateTime.ofInstant(p.getEventTimeUtc(), zone).toLocalDate().equals(workDate))
                .toList();

        ResolvedSchedule schedule = scheduleResolver.resolve(employeeId, workDate);
        ShiftSnapshot shift = resolveShiftSnapshot(schedule, workDate, zone, dayPunches);

        EngineInputs.ScheduledState state = switch (schedule.source()) {
            case TEMPORARY, EMPLOYEE_ASSIGNMENT, GROUP_ASSIGNMENT ->
                    schedule.hasShift() ? EngineInputs.ScheduledState.SCHEDULED
                                        : EngineInputs.ScheduledState.OFF;
            case NONE -> EngineInputs.ScheduledState.UNSCHEDULED;
        };

        boolean holiday = isHoliday(workDate);
        Map<UUID, BigDecimal> rates = loadRates();
        LeaveSnapshot leave = resolveLeave(employeeId, workDate);

        EngineInputs inputs = new EngineInputs(
                employeeId, workDate, zone, state, shift,
                EngineSnapshotMapper.toPunchSnapshots(dayPunches),
                holiday, leave, rates);

        EngineOutput output = TimeCardCalculator.compute(inputs);
        DailyTimeCard saved = upsert(employeeId, workDate, output);

        List<EmittedException> exceptions = output.exceptions().stream()
                .map(e -> new EmittedException(e.type(), e.severity(), e.detailsJson()))
                .toList();
        exceptionEventService.replaceForDay(employeeId, workDate, saved.getId(), exceptions);
        return saved;
    }

    private ShiftSnapshot resolveShiftSnapshot(ResolvedSchedule schedule, LocalDate workDate,
                                               ZoneId zone, List<PunchEvent> punches) {
        if (!schedule.hasShift()) {
            return null;
        }
        Optional<Shift> shift = shiftService.findCached(schedule.shiftId());
        if (shift.isEmpty()) {
            return null;
        }
        Shift s = shift.get();
        if (s.getShiftType() == ShiftType.FLOATING) {
            List<ShiftSnapshot> candidateSnapshots = s.getCandidateShiftIds().stream()
                    .map(shiftService::findCached)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(EngineSnapshotMapper::toShiftSnapshot)
                    .toList();
            return FloatingShiftSelector.select(workDate, zone,
                            EngineSnapshotMapper.toPunchSnapshots(punches), candidateSnapshots)
                    .orElse(null);
        }
        return EngineSnapshotMapper.toShiftSnapshot(s);
    }

    private boolean isHoliday(LocalDate workDate) {
        return holidayRepository.findAllByOrderByHolidayDateAsc().stream()
                .anyMatch(h -> {
                    if (h.isRecurringYearly()) {
                        return MonthDay.from(h.getHolidayDate()).equals(MonthDay.from(workDate));
                    }
                    return h.getHolidayDate().equals(workDate);
                });
    }

    private LeaveSnapshot resolveLeave(UUID employeeId, LocalDate workDate) {
        List<LeaveRequest> approved = leaveRequestRepository.findApprovedCovering(employeeId, workDate);
        if (approved.isEmpty()) {
            return null;
        }
        // If multiple leaves cover the day (shouldn't happen in practice), prefer
        // the longest-running one; ordering is stable across recomputes.
        LeaveRequest r = approved.stream()
                .min((a, b) -> {
                    long la = java.time.temporal.ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate());
                    long lb = java.time.temporal.ChronoUnit.DAYS.between(b.getStartDate(), b.getEndDate());
                    return Long.compare(lb, la);
                })
                .orElseThrow();
        LeaveType type = leaveTypeRepository.findById(r.getLeaveTypeId()).orElse(null);
        if (type == null) {
            return null;
        }
        return new LeaveSnapshot(type.getTimeCodeId(), r.isHalfDay());
    }

    private Map<UUID, BigDecimal> loadRates() {
        Map<UUID, BigDecimal> rates = new HashMap<>();
        for (TimeCode tc : timeCodeRepository.findAll()) {
            rates.put(tc.getId(), tc.getRate());
        }
        return rates;
    }

    private DailyTimeCard upsert(UUID employeeId, LocalDate workDate, EngineOutput output) {
        DailyTimeCard card = dailyTimeCardRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseGet(() -> {
                    DailyTimeCard fresh = new DailyTimeCard();
                    fresh.setEmployeeId(employeeId);
                    fresh.setWorkDate(workDate);
                    return fresh;
                });
        card.setComputedAt(Instant.now());
        card.setResolvedShiftId(output.resolvedShiftId());
        card.setScheduledStartUtc(output.scheduledStartUtc());
        card.setScheduledEndUtc(output.scheduledEndUtc());
        card.setActualStartUtc(output.actualStartUtc());
        card.setActualEndUtc(output.actualEndUtc());
        card.setWorkedMinutes(output.workedMinutes());
        card.setBreakMinutes(output.breakMinutes());
        card.setOvertimeMinutes(output.overtimeMinutes());
        card.setLateMinutes(output.lateMinutes());
        card.setEarlyOutMinutes(output.earlyOutMinutes());
        card.setStatus(output.status());
        // Engine never sets notes — preserve any manually-set value on recompute.
        if (output.notes() != null) {
            card.setNotes(output.notes());
        }

        card.getBreakdowns().clear();
        for (EngineOutput.BreakdownLine line : output.breakdowns()) {
            TimeCardBreakdown b = new TimeCardBreakdown();
            b.setDailyTimeCard(card);
            b.setTimeCodeId(line.timeCodeId());
            b.setMinutes(line.minutes());
            b.setRatedMinutes(line.ratedMinutes());
            b.setSequenceOrder(line.sequenceOrder());
            card.getBreakdowns().add(b);
        }
        return dailyTimeCardRepository.saveAndFlush(card);
    }
}

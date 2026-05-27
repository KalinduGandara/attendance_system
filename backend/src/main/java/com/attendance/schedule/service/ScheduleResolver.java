package com.attendance.schedule.service;

import com.attendance.organization.service.EmployeeService;
import com.attendance.platform.persistence.CacheConfig;
import com.attendance.schedule.domain.ScheduleAssignment;
import com.attendance.schedule.domain.ScheduleTemplate;
import com.attendance.schedule.domain.ScheduleTemplateDay;
import com.attendance.schedule.domain.TemporarySchedule;
import com.attendance.schedule.repository.ScheduleAssignmentRepository;
import com.attendance.schedule.repository.ScheduleTemplateRepository;
import com.attendance.schedule.repository.TemporaryScheduleRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure (in the sense of deterministic from inputs) schedule resolution.
 *
 * <p>Priority order, highest first:
 * <ol>
 *   <li>Temporary schedule covering the date (per-employee override) — most
 *       recently created wins ties.</li>
 *   <li>EMPLOYEE assignment whose date window covers the date — highest
 *       priority wins; same priority is broken by latest startDate.</li>
 *   <li>GROUP assignment for any group the employee belongs to — same
 *       tiebreak as employee assignments.</li>
 *   <li>None.</li>
 * </ol>
 *
 * <p>For a winning assignment, the day-in-cycle is computed as
 * {@code daysBetween(assignment.startDate, date) % template.cycleLengthDays}.
 * For WEEKLY templates whose startDate aligns to the org's week start, this
 * naturally yields Monday→0, Tuesday→1, etc.
 */
@Service
public class ScheduleResolver {

    private final TemporaryScheduleRepository temporaryRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final ScheduleTemplateRepository templateRepository;
    private final EmployeeService employeeService;

    public ScheduleResolver(TemporaryScheduleRepository temporaryRepository,
                            ScheduleAssignmentRepository assignmentRepository,
                            ScheduleTemplateRepository templateRepository,
                            EmployeeService employeeService) {
        this.temporaryRepository = temporaryRepository;
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public ResolvedSchedule resolve(UUID employeeId, LocalDate date) {
        // 1. Temporary schedule.
        List<TemporarySchedule> temps = temporaryRepository.findForEmployeeOnDate(employeeId, date);
        Optional<TemporarySchedule> winningTemp = temps.stream()
                .max(Comparator.comparing(TemporarySchedule::getCreatedAt));
        if (winningTemp.isPresent()) {
            TemporarySchedule t = winningTemp.get();
            return new ResolvedSchedule(employeeId, date,
                    ResolvedSchedule.Source.TEMPORARY,
                    t.getShiftId(), null, -1, null, t.getId());
        }

        // 2. Direct employee assignment.
        Optional<ScheduleAssignment> empAssignment =
                pickWinner(assignmentRepository.findEmployeeAssignmentsForDate(employeeId, date));
        if (empAssignment.isPresent()) {
            return resolveAssignment(employeeId, date, empAssignment.get(),
                    ResolvedSchedule.Source.EMPLOYEE_ASSIGNMENT);
        }

        // 3. Group assignment.
        Set<UUID> groupIds = employeeService.groupIdsForEmployee(employeeId);
        if (!groupIds.isEmpty()) {
            Optional<ScheduleAssignment> grpAssignment = pickWinner(
                    assignmentRepository.findGroupAssignmentsForDate(groupIds, date));
            if (grpAssignment.isPresent()) {
                return resolveAssignment(employeeId, date, grpAssignment.get(),
                        ResolvedSchedule.Source.GROUP_ASSIGNMENT);
            }
        }

        return ResolvedSchedule.none(employeeId, date);
    }

    private ResolvedSchedule resolveAssignment(UUID employeeId, LocalDate date,
                                               ScheduleAssignment a,
                                               ResolvedSchedule.Source source) {
        Optional<ScheduleTemplate> template = findCachedTemplate(a.getTemplateId());
        if (template.isEmpty()) {
            return new ResolvedSchedule(employeeId, date, source,
                    null, a.getTemplateId(), -1, a.getId(), null);
        }
        ScheduleTemplate t = template.get();
        int dayIndex = dayIndexFor(a.getStartDate(), date, t.getCycleLengthDays());
        UUID shiftId = t.getDays().stream()
                .filter(d -> d.getDayIndex() == dayIndex)
                .findFirst()
                .map(ScheduleTemplateDay::getShiftId)
                .orElse(null);
        return new ResolvedSchedule(employeeId, date, source,
                shiftId, t.getId(), dayIndex, a.getId(), null);
    }

    @Cacheable(value = CacheConfig.SCHEDULE_TEMPLATES, key = "#id", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public Optional<ScheduleTemplate> findCachedTemplate(UUID id) {
        return templateRepository.findById(id);
    }

    static int dayIndexFor(LocalDate assignmentStart, LocalDate target, int cycleLengthDays) {
        if (cycleLengthDays <= 0) {
            return 0;
        }
        long delta = ChronoUnit.DAYS.between(assignmentStart, target);
        long mod = delta % cycleLengthDays;
        if (mod < 0) {
            mod += cycleLengthDays;
        }
        return (int) mod;
    }

    private static Optional<ScheduleAssignment> pickWinner(List<ScheduleAssignment> candidates) {
        return candidates.stream()
                .max(Comparator.comparingInt(ScheduleAssignment::getPriority)
                        .thenComparing(ScheduleAssignment::getStartDate));
    }
}

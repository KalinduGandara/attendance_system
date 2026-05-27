package com.attendance.schedule.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.schedule.domain.TemporarySchedule;
import com.attendance.schedule.repository.TemporaryScheduleRepository;
import com.attendance.schedule.web.ScheduleDtos;
import com.attendance.shift.repository.ShiftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TemporaryScheduleService {

    private final TemporaryScheduleRepository repository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;

    public TemporaryScheduleService(TemporaryScheduleRepository repository,
                                    EmployeeRepository employeeRepository,
                                    ShiftRepository shiftRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.TemporaryScheduleResponse> list(UUID employeeId,
                                                             LocalDate from,
                                                             LocalDate to) {
        return repository.search(employeeId, from, to).stream()
                .map(TemporaryScheduleService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDtos.TemporaryScheduleResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ScheduleDtos.TemporaryScheduleResponse create(ScheduleDtos.TemporaryScheduleRequest req) {
        validate(req);
        TemporarySchedule t = new TemporarySchedule();
        apply(t, req);
        return toResponse(repository.saveAndFlush(t));
    }

    @Transactional
    public ScheduleDtos.TemporaryScheduleResponse update(UUID id,
                                                        ScheduleDtos.TemporaryScheduleRequest req) {
        TemporarySchedule t = findOrThrow(id);
        if (req.expectedVersion() != null && !req.expectedVersion().equals(t.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Temporary schedule was modified by another request; reload and retry");
        }
        validate(req);
        apply(t, req);
        return toResponse(repository.saveAndFlush(t));
    }

    @Transactional
    public void delete(UUID id) {
        TemporarySchedule t = findOrThrow(id);
        repository.delete(t);
    }

    private void validate(ScheduleDtos.TemporaryScheduleRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw badRequest("endDate must be on or after startDate");
        }
        if (!employeeRepository.existsById(req.employeeId())) {
            throw badRequest("employee not found");
        }
        if (req.shiftId() != null && !shiftRepository.existsById(req.shiftId())) {
            throw badRequest("shift not found");
        }
    }

    private void apply(TemporarySchedule t, ScheduleDtos.TemporaryScheduleRequest req) {
        t.setEmployeeId(req.employeeId());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setShiftId(req.shiftId());
        t.setReason(req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
    }

    private TemporarySchedule findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Temporary schedule not found"));
    }

    static ScheduleDtos.TemporaryScheduleResponse toResponse(TemporarySchedule t) {
        return new ScheduleDtos.TemporaryScheduleResponse(
                t.getId(), t.getEmployeeId(), t.getStartDate(), t.getEndDate(),
                t.getShiftId(), t.getReason(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
    }

    private static ApiException badRequest(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation", msg);
    }
}

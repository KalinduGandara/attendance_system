package com.attendance.schedule.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.schedule.domain.AssignmentTargetType;
import com.attendance.schedule.domain.ScheduleAssignment;
import com.attendance.schedule.repository.ScheduleAssignmentRepository;
import com.attendance.schedule.repository.ScheduleTemplateRepository;
import com.attendance.schedule.web.ScheduleDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ScheduleAssignmentService {

    private final ScheduleAssignmentRepository repository;
    private final ScheduleTemplateRepository templateRepository;
    private final EmployeeRepository employeeRepository;
    private final UserGroupRepository userGroupRepository;

    public ScheduleAssignmentService(ScheduleAssignmentRepository repository,
                                     ScheduleTemplateRepository templateRepository,
                                     EmployeeRepository employeeRepository,
                                     UserGroupRepository userGroupRepository) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.employeeRepository = employeeRepository;
        this.userGroupRepository = userGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.AssignmentResponse> list(AssignmentTargetType targetType,
                                                      UUID targetId,
                                                      UUID templateId) {
        return repository.search(targetType, targetId, templateId).stream()
                .map(ScheduleAssignmentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDtos.AssignmentResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ScheduleDtos.AssignmentResponse create(ScheduleDtos.AssignmentRequest req) {
        validate(req);
        ScheduleAssignment a = new ScheduleAssignment();
        apply(a, req);
        return toResponse(repository.saveAndFlush(a));
    }

    @Transactional
    public ScheduleDtos.AssignmentResponse update(UUID id, ScheduleDtos.AssignmentRequest req) {
        ScheduleAssignment a = findOrThrow(id);
        if (req.expectedVersion() != null && !req.expectedVersion().equals(a.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Assignment was modified by another request; reload and retry");
        }
        validate(req);
        apply(a, req);
        return toResponse(repository.saveAndFlush(a));
    }

    @Transactional
    public void delete(UUID id) {
        ScheduleAssignment a = findOrThrow(id);
        repository.delete(a);
    }

    private void validate(ScheduleDtos.AssignmentRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw badRequest("endDate must be on or after startDate");
        }
        if (!templateRepository.existsById(req.templateId())) {
            throw badRequest("template not found");
        }
        if (req.targetType() == AssignmentTargetType.EMPLOYEE) {
            if (!employeeRepository.existsById(req.targetId())) {
                throw badRequest("target employee not found");
            }
        } else {
            if (!userGroupRepository.existsById(req.targetId())) {
                throw badRequest("target group not found");
            }
        }
    }

    private void apply(ScheduleAssignment a, ScheduleDtos.AssignmentRequest req) {
        a.setTargetType(req.targetType());
        a.setTargetId(req.targetId());
        a.setTemplateId(req.templateId());
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setPriority(req.priority() == null ? 0 : req.priority());
    }

    private ScheduleAssignment findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Assignment not found"));
    }

    static ScheduleDtos.AssignmentResponse toResponse(ScheduleAssignment a) {
        return new ScheduleDtos.AssignmentResponse(
                a.getId(), a.getTargetType(), a.getTargetId(), a.getTemplateId(),
                a.getStartDate(), a.getEndDate(), a.getPriority(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getVersion());
    }

    private static ApiException badRequest(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation", msg);
    }
}

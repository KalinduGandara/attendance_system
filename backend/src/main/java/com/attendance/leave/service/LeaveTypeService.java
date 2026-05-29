package com.attendance.leave.service;

import com.attendance.common.error.ApiException;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.leave.web.LeaveDtos;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository repository;
    private final TimeCodeRepository timeCodeRepository;

    public LeaveTypeService(LeaveTypeRepository repository, TimeCodeRepository timeCodeRepository) {
        this.repository = repository;
        this.timeCodeRepository = timeCodeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveDtos.LeaveTypeResponse> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(t -> toResponse(t, timeCodeRepository.findById(t.getTimeCodeId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveDtos.LeaveTypeResponse get(UUID id) {
        LeaveType t = repository.findById(id).orElseThrow(this::notFound);
        return toResponse(t, timeCodeRepository.findById(t.getTimeCodeId()).orElse(null));
    }

    @Transactional
    public LeaveDtos.LeaveTypeResponse create(LeaveDtos.LeaveTypeRequest req) {
        validateTimeCode(req.timeCodeId());
        if (repository.findByName(req.name()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate", "Leave type name already used");
        }
        LeaveType t = new LeaveType();
        apply(t, req);
        LeaveType saved = repository.saveAndFlush(t);
        return toResponse(saved, timeCodeRepository.findById(saved.getTimeCodeId()).orElse(null));
    }

    @Transactional
    public LeaveDtos.LeaveTypeResponse update(UUID id, LeaveDtos.LeaveTypeRequest req) {
        LeaveType t = repository.findById(id).orElseThrow(this::notFound);
        validateTimeCode(req.timeCodeId());
        repository.findByName(req.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "duplicate",
                            "Leave type name already used");
                });
        apply(t, req);
        LeaveType saved = repository.saveAndFlush(t);
        return toResponse(saved, timeCodeRepository.findById(saved.getTimeCodeId()).orElse(null));
    }

    @Transactional
    public void delete(UUID id) {
        LeaveType t = repository.findById(id).orElseThrow(this::notFound);
        repository.delete(t);
    }

    private void apply(LeaveType t, LeaveDtos.LeaveTypeRequest req) {
        t.setName(req.name());
        t.setTimeCodeId(req.timeCodeId());
        t.setDefaultAnnualDays(req.defaultAnnualDays());
        t.setRequiresApproval(req.requiresApproval());
        t.setAccrualRuleJson(req.accrualRuleJson());
        t.setActive(req.active());
    }

    private void validateTimeCode(UUID timeCodeId) {
        TimeCode tc = timeCodeRepository.findById(timeCodeId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "validation", "Unknown time code"));
        if (tc.getCategory() != TimeCodeCategory.LEAVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Leave type must reference a LEAVE-category time code");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Leave type not found");
    }

    private LeaveDtos.LeaveTypeResponse toResponse(LeaveType t, TimeCode tc) {
        return new LeaveDtos.LeaveTypeResponse(
                t.getId(),
                t.getTimeCodeId(),
                tc == null ? null : tc.getCode(),
                t.getName(),
                t.getDefaultAnnualDays(),
                t.isRequiresApproval(),
                t.getAccrualRuleJson(),
                t.isActive(),
                t.getVersion());
    }
}

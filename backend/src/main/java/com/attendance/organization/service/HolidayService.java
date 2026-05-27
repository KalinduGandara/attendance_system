package com.attendance.organization.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.Holiday;
import com.attendance.organization.domain.UserGroup;
import com.attendance.organization.repository.HolidayRepository;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.web.HolidayDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final UserGroupRepository userGroupRepository;

    public HolidayService(HolidayRepository holidayRepository,
                          UserGroupRepository userGroupRepository) {
        this.holidayRepository = holidayRepository;
        this.userGroupRepository = userGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<HolidayDtos.HolidayResponse> list(LocalDate from, LocalDate to) {
        List<Holiday> holidays = (from != null && to != null)
                ? holidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(from, to)
                : holidayRepository.findAllByOrderByHolidayDateAsc();
        return holidays.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HolidayDtos.HolidayResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public HolidayDtos.HolidayResponse create(HolidayDtos.HolidayRequest req) {
        Set<UserGroup> groups = resolveGroups(req.groupIds());
        Holiday h = new Holiday();
        apply(h, req, groups);
        return toResponse(holidayRepository.save(h));
    }

    @Transactional
    public HolidayDtos.HolidayResponse update(UUID id, HolidayDtos.HolidayRequest req) {
        Holiday h = findOrThrow(id);
        Set<UserGroup> groups = resolveGroups(req.groupIds());
        apply(h, req, groups);
        return toResponse(h);
    }

    @Transactional
    public void delete(UUID id) {
        Holiday h = findOrThrow(id);
        holidayRepository.delete(h);
    }

    private void apply(Holiday h, HolidayDtos.HolidayRequest req, Set<UserGroup> groups) {
        h.setName(req.name().trim());
        h.setHolidayDate(req.holidayDate());
        h.setRecurringYearly(req.recurringYearly());
        h.setPaid(req.paid());
        h.setDescription(req.description());
        h.getGroups().clear();
        h.getGroups().addAll(groups);
    }

    private Set<UserGroup> resolveGroups(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<UserGroup> found = userGroupRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "One or more groups not found");
        }
        return new HashSet<>(found);
    }

    private Holiday findOrThrow(UUID id) {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Holiday not found"));
    }

    private HolidayDtos.HolidayResponse toResponse(Holiday h) {
        List<HolidayDtos.GroupRef> groups = h.getGroups().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(g -> new HolidayDtos.GroupRef(g.getId(), g.getName()))
                .toList();
        return new HolidayDtos.HolidayResponse(h.getId(), h.getName(), h.getHolidayDate(),
                h.isRecurringYearly(), h.isPaid(), h.getDescription(), groups,
                h.getCreatedAt(), h.getUpdatedAt(), h.getVersion());
    }
}

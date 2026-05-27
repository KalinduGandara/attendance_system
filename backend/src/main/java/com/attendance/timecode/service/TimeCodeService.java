package com.attendance.timecode.service;

import com.attendance.common.error.ApiException;
import com.attendance.platform.persistence.CacheConfig;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import com.attendance.timecode.web.TimeCodeDtos;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TimeCodeService {

    private final TimeCodeRepository repository;

    public TimeCodeService(TimeCodeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TimeCodeDtos.TimeCodeResponse> list(TimeCodeCategory category, Boolean activeOnly) {
        List<TimeCode> rows = category == null
                ? repository.findAllByOrderByCodeAsc()
                : repository.findByCategoryOrderByCodeAsc(category);
        return rows.stream()
                .filter(t -> activeOnly == null || !activeOnly || t.isActive())
                .map(TimeCodeService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeCodeDtos.TimeCodeResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Cache by id for the engine's hot path. The cached value is the entity
     * itself so we can use it inside services that compose with shifts.
     */
    @Cacheable(value = CacheConfig.TIME_CODES, key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<TimeCode> findCached(UUID id) {
        return repository.findById(id);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.TIME_CODES, allEntries = true)
    public TimeCodeDtos.TimeCodeResponse create(TimeCodeDtos.TimeCodeRequest req) {
        if (repository.existsByCodeIgnoreCase(req.code().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Time code with this code already exists");
        }
        TimeCode t = new TimeCode();
        apply(t, req);
        return toResponse(repository.save(t));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.TIME_CODES, allEntries = true)
    public TimeCodeDtos.TimeCodeResponse update(UUID id, TimeCodeDtos.TimeCodeRequest req) {
        TimeCode t = findOrThrow(id);
        if (!t.getCode().equalsIgnoreCase(req.code().trim())
                && repository.existsByCodeIgnoreCase(req.code().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Time code with this code already exists");
        }
        apply(t, req);
        return toResponse(t);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.TIME_CODES, allEntries = true)
    public void delete(UUID id) {
        TimeCode t = findOrThrow(id);
        try {
            repository.delete(t);
            repository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Time code is referenced by shifts or breaks; deactivate instead");
        }
    }

    private void apply(TimeCode t, TimeCodeDtos.TimeCodeRequest req) {
        t.setCode(req.code().trim());
        t.setName(req.name().trim());
        t.setCategory(req.category());
        t.setRate(req.rate());
        t.setColor(req.color());
        t.setPaid(Boolean.TRUE.equals(req.paid()));
        t.setCountsForAttendance(Boolean.TRUE.equals(req.countsForAttendance()));
        t.setDescription(req.description() == null || req.description().isBlank()
                ? null : req.description().trim());
        t.setActive(req.active() == null ? true : req.active());
    }

    private TimeCode findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "not-found", "Time code not found"));
    }

    static TimeCodeDtos.TimeCodeResponse toResponse(TimeCode t) {
        return new TimeCodeDtos.TimeCodeResponse(
                t.getId(), t.getCode(), t.getName(), t.getCategory(), t.getRate(),
                t.getColor(), t.isPaid(), t.isCountsForAttendance(),
                t.getDescription(), t.isActive(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
    }
}

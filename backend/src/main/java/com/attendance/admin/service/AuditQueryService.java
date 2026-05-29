package com.attendance.admin.service;

import com.attendance.common.audit.AuditEvent;
import com.attendance.common.audit.AuditEventRepository;
import com.attendance.common.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Read-only access to the append-only audit trail for the Audit Log viewer.
 * The audit table is owned by the shared kernel ({@code common.audit}); this
 * service adds filtering/paging without any module reaching into another
 * module's repository.
 */
@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(UUID actorUserId, String action, String entityType, UUID entityId,
                                   LocalDate from, LocalDate to, int page, int size) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        // `to` is inclusive of the whole day → exclusive bound at the next midnight.
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return repository.search(actorUserId, blankToNull(action), blankToNull(entityType), entityId,
                fromInstant, toInstant, pageable);
    }

    @Transactional(readOnly = true)
    public AuditEvent get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Audit event not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

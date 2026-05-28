package com.attendance.timecard.service;

import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.web.TimeCardDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PunchEventReadService {

    private final PunchEventRepository repository;

    public PunchEventReadService(PunchEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TimeCardDtos.PunchEventResponse> list(UUID employeeId,
                                                      PunchEventStatus status,
                                                      Instant from,
                                                      Instant to,
                                                      int page,
                                                      int size) {
        int safeSize = Math.min(Math.max(1, size), 200);
        Page<PunchEvent> result = repository.search(employeeId, status, from, to,
                PageRequest.of(Math.max(0, page), safeSize));
        return result.getContent().stream().map(PunchEventReadService::toResponse).toList();
    }

    private static TimeCardDtos.PunchEventResponse toResponse(PunchEvent p) {
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
}

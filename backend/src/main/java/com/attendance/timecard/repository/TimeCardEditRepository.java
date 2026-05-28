package com.attendance.timecard.repository;

import com.attendance.timecard.domain.TimeCardEdit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeCardEditRepository extends JpaRepository<TimeCardEdit, UUID> {
    List<TimeCardEdit> findByDailyTimeCardIdOrderByEditedAtAsc(UUID dailyTimeCardId);
}

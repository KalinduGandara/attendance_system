package com.attendance.organization.repository;

import com.attendance.organization.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    List<Holiday> findAllByOrderByHolidayDateAsc();
}

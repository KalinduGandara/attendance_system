package com.attendance.timecode.repository;

import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeCodeRepository extends JpaRepository<TimeCode, UUID> {

    Optional<TimeCode> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<TimeCode> findAllByOrderByCodeAsc();

    List<TimeCode> findByCategoryOrderByCodeAsc(TimeCodeCategory category);
}

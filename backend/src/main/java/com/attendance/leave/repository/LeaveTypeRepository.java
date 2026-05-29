package com.attendance.leave.repository;

import com.attendance.leave.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    List<LeaveType> findAllByOrderByNameAsc();

    Optional<LeaveType> findByName(String name);
}

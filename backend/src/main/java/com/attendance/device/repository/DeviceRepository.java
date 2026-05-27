package com.attendance.device.repository;

import com.attendance.device.domain.Device;
import com.attendance.device.domain.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @Query("""
           SELECT d FROM Device d
           WHERE (:q IS NULL OR LOWER(d.name)     LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(d.location) LIKE LOWER(CONCAT('%', :q, '%')))
             AND (:status IS NULL OR d.status = :status)
           """)
    Page<Device> search(@Param("q") String q,
                        @Param("status") DeviceStatus status,
                        Pageable pageable);
}

package org.example.repository;

import org.example.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findByStatus(String status);

    @Query("""
            SELECT d
            FROM Device d
            JOIN UserDevice ud ON ud.deviceId = d.id
            WHERE ud.userId = :userId
            """)
    List<Device> findByUserId(@Param("userId") Long userId);
}
//ON   = hangi kolonlar eşitse birleştir
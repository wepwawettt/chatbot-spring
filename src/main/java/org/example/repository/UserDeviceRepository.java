package org.example.repository;

import org.example.entity.UserDevice;
import org.example.entity.UserDeviceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UserDeviceId> {
    List<UserDevice> findByUserId(Long userId);

    boolean existsByUserIdAndDeviceId(Long userId, Long deviceId);

    void deleteByUserIdAndDeviceId(Long userId, Long deviceId);
}

package com.smarthome.repository;

import com.smarthome.model.DeviceActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceActivityRepository extends JpaRepository<DeviceActivity, Long> {
    List<DeviceActivity> findByDeviceIdOrderByActivityIdDesc(String deviceId);
    void deleteByDeviceId(String deviceId);
}

package dev.yeon.iotsensorplatform.device.repository;

import dev.yeon.iotsensorplatform.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByZoneIdIn(List<Long> zoneIds);
    List<Device> findAllByZone_Factory_Id(Long factoryId);
    List<Device> findByExpectedIntervalSecondsIsNotNull();

    @Query("SELECT d.id FROM Device d")
    List<Long> findAllIds();
}

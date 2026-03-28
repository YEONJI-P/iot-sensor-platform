package dev.yeon.iotsensorplatform.device.repository;

import dev.yeon.iotsensorplatform.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByGroupIdIn(List<Long> groupIds);
    List<Device> findAllByGroup_Organization_Id(Long organizationId);

    @Query("SELECT d.id FROM Device d")
    List<Long> findAllIds();
}

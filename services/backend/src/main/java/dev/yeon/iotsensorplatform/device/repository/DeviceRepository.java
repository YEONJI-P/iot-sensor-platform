package dev.yeon.iotsensorplatform.device.repository;

import dev.yeon.iotsensorplatform.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByZoneIdIn(List<Long> zoneIds);
    List<Device> findAllByZone_Factory_Id(Long factoryId);

    // freshness 판정용. zone-코호트 비교를 트랜잭션 밖에서 하므로 zone을 함께 로드한다.
    @Query("SELECT d FROM Device d JOIN FETCH d.zone WHERE d.expectedIntervalSeconds IS NOT NULL")
    List<Device> findMonitoredWithZone();

    @Query("SELECT d.id FROM Device d")
    List<Long> findAllIds();

    @Query("SELECT d.id FROM Device d WHERE d.zone.factory.id = :factoryId")
    List<Long> findIdsByFactoryId(Long factoryId);

    @Query("SELECT d.id FROM Device d WHERE d.zone.id IN :zoneIds")
    List<Long> findIdsByZoneIdIn(List<Long> zoneIds);
}

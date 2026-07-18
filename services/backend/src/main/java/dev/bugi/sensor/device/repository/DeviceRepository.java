package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    // 수신 API 는 물리 노드 code 로 장치를 찾는다(UK).
    Optional<Device> findByCode(String code);

    List<Device> findAllByZoneIdIn(List<Long> zoneIds);
    List<Device> findAllByZone_Factory_Id(Long factoryId);

    @Query("""
            SELECT d FROM Device d
            LEFT JOIN FETCH d.zone z
            LEFT JOIN FETCH z.factory f
            WHERE d.id IN :deviceIds
            ORDER BY f.name, f.id, z.name, z.id, d.name, d.id
            """)
    List<Device> findOverviewDevicesByIdIn(List<Long> deviceIds);

    @Query("SELECT d.id FROM Device d")
    List<Long> findAllIds();

    @Query("SELECT d.id FROM Device d WHERE d.zone.factory.id = :factoryId")
    List<Long> findIdsByFactoryId(Long factoryId);

    @Query("SELECT d.id FROM Device d WHERE d.zone.id IN :zoneIds")
    List<Long> findIdsByZoneIdIn(List<Long> zoneIds);
}

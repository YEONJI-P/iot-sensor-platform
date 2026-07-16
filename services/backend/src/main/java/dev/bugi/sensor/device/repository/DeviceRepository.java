package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByZoneIdIn(List<Long> zoneIds);
    List<Device> findAllByZone_Factory_Id(Long factoryId);

    @Query("SELECT d.id FROM Device d")
    List<Long> findAllIds();

    @Query("SELECT d.id FROM Device d WHERE d.zone.factory.id = :factoryId")
    List<Long> findIdsByFactoryId(Long factoryId);

    @Query("SELECT d.id FROM Device d WHERE d.zone.id IN :zoneIds")
    List<Long> findIdsByZoneIdIn(List<Long> zoneIds);
}

package dev.bugi.sensor.factory.repository;

import dev.bugi.sensor.factory.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findAllByFactoryId(Long factoryId);
}

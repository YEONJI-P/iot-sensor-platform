package dev.yeon.iotsensorplatform.factory.repository;

import dev.yeon.iotsensorplatform.factory.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findAllByFactoryId(Long factoryId);
}

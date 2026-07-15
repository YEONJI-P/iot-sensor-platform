package dev.yeon.iotsensorplatform.factory.repository;

import dev.yeon.iotsensorplatform.factory.entity.ZoneUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneUserRepository extends JpaRepository<ZoneUser, Long> {
    List<ZoneUser> findAllByUserId(Long userId);
    List<ZoneUser> findAllByZoneId(Long zoneId);
    boolean existsByZoneIdAndUserId(Long zoneId, Long userId);
    void deleteByZoneIdAndUserId(Long zoneId, Long userId);
}

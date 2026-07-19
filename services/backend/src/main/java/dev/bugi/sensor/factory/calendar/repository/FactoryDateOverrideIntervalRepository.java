package dev.bugi.sensor.factory.calendar.repository;

import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverrideInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FactoryDateOverrideIntervalRepository extends JpaRepository<FactoryDateOverrideInterval, Long> {

    @Query("SELECT i FROM FactoryDateOverrideInterval i WHERE i.override.id IN :overrideIds")
    List<FactoryDateOverrideInterval> findAllByOverrideIdIn(@Param("overrideIds") Collection<Long> overrideIds);

    @Modifying
    @Query("DELETE FROM FactoryDateOverrideInterval i WHERE i.override.calendar.factoryId = :factoryId")
    void deleteAllByFactoryId(@Param("factoryId") Long factoryId);
}

package dev.bugi.sensor.factory.calendar.repository;

import dev.bugi.sensor.factory.calendar.entity.FactoryWeeklyInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FactoryWeeklyIntervalRepository extends JpaRepository<FactoryWeeklyInterval, Long> {

    @Query("SELECT i FROM FactoryWeeklyInterval i WHERE i.calendar.factoryId IN :factoryIds")
    List<FactoryWeeklyInterval> findAllByFactoryIdIn(@Param("factoryIds") Collection<Long> factoryIds);

    @Query("SELECT i FROM FactoryWeeklyInterval i WHERE i.calendar.factoryId = :factoryId ORDER BY i.isoDay, i.startMinute")
    List<FactoryWeeklyInterval> findAllByFactoryId(@Param("factoryId") Long factoryId);

    @Modifying
    @Query("DELETE FROM FactoryWeeklyInterval i WHERE i.calendar.factoryId = :factoryId")
    void deleteAllByFactoryId(@Param("factoryId") Long factoryId);
}

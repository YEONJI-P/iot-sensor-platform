package dev.bugi.sensor.factory.calendar.repository;

import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface FactoryDateOverrideRepository extends JpaRepository<FactoryDateOverride, Long> {

    @Query("SELECT o FROM FactoryDateOverride o WHERE o.calendar.factoryId IN :factoryIds AND o.localDate BETWEEN :from AND :to")
    List<FactoryDateOverride> findAllInRange(@Param("factoryIds") Collection<Long> factoryIds,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Query("SELECT o FROM FactoryDateOverride o WHERE o.calendar.factoryId = :factoryId ORDER BY o.localDate")
    List<FactoryDateOverride> findAllByFactoryId(@Param("factoryId") Long factoryId);

    @Modifying
    @Query("DELETE FROM FactoryDateOverride o WHERE o.calendar.factoryId = :factoryId")
    void deleteAllByFactoryId(@Param("factoryId") Long factoryId);
}

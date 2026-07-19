package dev.bugi.sensor.factory.calendar.repository;

import dev.bugi.sensor.factory.calendar.entity.FactoryOperatingCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FactoryOperatingCalendarRepository extends JpaRepository<FactoryOperatingCalendar, Long> {

    @Query("SELECT c FROM FactoryOperatingCalendar c JOIN FETCH c.factory WHERE c.factoryId IN :factoryIds")
    List<FactoryOperatingCalendar> findAllWithFactoryByFactoryIdIn(@Param("factoryIds") Collection<Long> factoryIds);

    @Query("SELECT c FROM FactoryOperatingCalendar c JOIN FETCH c.factory ORDER BY c.factory.name")
    List<FactoryOperatingCalendar> findAllWithFactoryOrderByFactoryName();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FactoryOperatingCalendar c JOIN FETCH c.factory WHERE c.factoryId = :factoryId")
    Optional<FactoryOperatingCalendar> findForUpdate(@Param("factoryId") Long factoryId);
}

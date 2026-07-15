package dev.bugi.sensor.factory.repository;

import dev.bugi.sensor.factory.entity.Factory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryRepository extends JpaRepository<Factory, Long> {
    boolean existsByName(String name);
}

package dev.yeon.iotsensorplatform.factory.repository;

import dev.yeon.iotsensorplatform.factory.entity.Factory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryRepository extends JpaRepository<Factory, Long> {
    boolean existsByName(String name);
}

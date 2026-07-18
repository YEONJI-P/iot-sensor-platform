package dev.bugi.sensor.sensordata.repository;

import dev.bugi.sensor.sensordata.entity.MeasurementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementBatchRepository extends JpaRepository<MeasurementBatch, Long> {
}

package dev.yeon.iotsensorplatform.sensordata.failure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedReadingRepository extends JpaRepository<FailedReading, Long> {
}

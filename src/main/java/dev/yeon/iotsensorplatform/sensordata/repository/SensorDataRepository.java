// SensorDataRepository.java
package dev.yeon.iotsensorplatform.sensordata.repository;
import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {
    List<SensorData> findAllByDeviceIdOrderByRecordedAtDesc(Long deviceId);
    List<SensorData> findAllByDeviceUserEmailOrderByRecordedAtDesc(String email);
}
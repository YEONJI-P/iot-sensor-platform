// AlertRepository.java
package dev.yeon.iotsensorplatform.alert.repository;
import dev.yeon.iotsensorplatform.alert.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findAllByDeviceIdOrderByCreatedAtDesc(Long deviceId);
    List<Alert> findAllByDeviceUserEmailOrderByCreatedAtDesc(String email);
}
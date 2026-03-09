// DeviceRepository.java
package dev.yeon.iotsensorplatform.device.repository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByUserId(Long userId);
}
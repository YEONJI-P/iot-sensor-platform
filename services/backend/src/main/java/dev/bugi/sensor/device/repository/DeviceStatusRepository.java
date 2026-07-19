package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Long> {

    // freshness 판정용. zone-코호트 비교를 트랜잭션 밖에서 하므로 device·zone 을 함께 로드한다.
    // JOIN 이라 수신 이력이 없는(status 행이 없는) 장치와 zone 없는 장치는 자연히 빠진다.
    @Query("""
            SELECT s FROM DeviceStatus s
            JOIN FETCH s.device d
            JOIN FETCH d.zone z
            JOIN FETCH z.factory
            WHERE d.expectedIntervalSeconds IS NOT NULL
            """)
    List<DeviceStatus> findMonitoredWithDeviceAndZone();
}

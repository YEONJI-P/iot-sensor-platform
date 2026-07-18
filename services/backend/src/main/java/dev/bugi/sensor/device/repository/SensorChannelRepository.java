package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.SensorChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SensorChannelRepository extends JpaRepository<SensorChannel, Long> {

    // 한 물리 Device 의 채널 전체. 수신 시 code→channel Map 구성 소스.
    List<SensorChannel> findByDeviceId(Long deviceId);

    // 대시보드 드롭다운 소스(접근 범위 필터). device·zone 을 함께 로드해 트랜잭션 밖 매핑에서 LAZY 접근을 피한다.
    @Query("""
            SELECT c FROM SensorChannel c
            JOIN FETCH c.device d
            LEFT JOIN FETCH d.zone
            WHERE d.id IN :deviceIds
            ORDER BY c.id
            """)
    List<SensorChannel> findByDeviceIdInWithDeviceAndZone(List<Long> deviceIds);
}

package dev.bugi.sensor.sensordata.repository;

import dev.bugi.sensor.sensordata.entity.SensorReading;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    // 채널별 최근 N건. 관측 시각은 batch.observedAt 에 있으므로 batch 를 join 해 정렬한다.
    // (대시보드 차트·설명용 윈도우 소스)
    @Query("""
            SELECT r FROM SensorReading r
            JOIN FETCH r.batch b
            WHERE r.channel.id = :channelId
            ORDER BY b.observedAt DESC
            """)
    List<SensorReading> findByChannelIdOrderByObservedAtDesc(Long channelId, Pageable pageable);
}

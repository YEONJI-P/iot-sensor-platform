package dev.bugi.sensor.sensordata.repository;

import dev.bugi.sensor.sensordata.entity.SensorReading;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    // 채널별 최근 N건. 관측 시각은 batch.observedAt 에 있으므로 batch 를 join 해 정렬한다.
    // 같은 observedAt(같은 초 등) 동점은 r.id DESC 로 안정 정렬한다. (대시보드 차트·설명용 윈도우 소스)
    @Query("""
            SELECT r FROM SensorReading r
            JOIN FETCH r.batch b
            WHERE r.channel.id = :channelId
            ORDER BY b.observedAt DESC, r.id DESC
            """)
    List<SensorReading> findByChannelIdOrderByObservedAtDesc(Long channelId, Pageable pageable);

    /**
     * 여러 채널의 최신 판독을 한 번에 가져온다. PostgreSQL DISTINCT ON으로 채널당 한 행만
     * 남기고, 같은 observed_at은 reading PK 내림차순으로 결정적으로 고른다.
     */
    @Query(value = """
            SELECT DISTINCT ON (r.channel_id)
                   r.channel_id   AS "channelId",
                   r.value        AS "value",
                   b.observed_at  AS "observedAt",
                   b.received_at  AS "receivedAt",
                   r.id           AS "readingId"
            FROM sensor_reading r
            JOIN measurement_batch b ON b.id = r.batch_id
            WHERE r.channel_id IN (:channelIds)
            ORDER BY r.channel_id,
                     b.observed_at DESC NULLS LAST,
                     r.id DESC
            """, nativeQuery = true)
    List<LatestReadingProjection> findLatestByChannelIds(@Param("channelIds") List<Long> channelIds);

    interface LatestReadingProjection {
        Long getChannelId();
        Double getValue();
        Instant getObservedAt();
        Instant getReceivedAt();
        Long getReadingId();
    }
}

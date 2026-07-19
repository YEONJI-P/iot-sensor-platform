package dev.bugi.sensor.alert.repository;

import dev.bugi.sensor.alert.dto.EnrichTarget;
import dev.bugi.sensor.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    // 전체 알림 목록(대시보드)은 device 범위로 스코핑한다 — 임계·freshness alert 모두 device_id 를 가진다.
    Page<Alert> findByDeviceIdIn(List<Long> deviceIds, Pageable pageable);

    // 채널 화면 조회는 channel_id 단독 필터(최소 범위).
    List<Alert> findByChannelIdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    // 보강 대상은 임계 alert 만(channel is not null). sensor_type←quantityKind, unit←channel.unit.
    @Query("select new dev.bugi.sensor.alert.dto.EnrichTarget(a.id, a.channel.id, a.device.name, a.channel.quantityKind, a.channel.unit, a.sensorValue, a.thresholdValue, a.channel.thresholdDirection, a.message) from Alert a where a.evidence is null and a.channel is not null order by a.createdAt desc")
    List<EnrichTarget> findEnrichTargets(Pageable pageable);

    @Query(value = """
            SELECT *
            FROM alert
            WHERE channel_id = :channelId
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Alert> findRecentByChannelId(@Param("channelId") Long channelId, @Param("limit") int limit);

    @Query(value = """
            SELECT to_char(created_at, 'YYYY-MM-DD') AS date,
                   COUNT(*)                          AS count
            FROM alert
            WHERE channel_id = :channelId
              AND created_at >= :startDate
            GROUP BY to_char(created_at, 'YYYY-MM-DD')
            ORDER BY date ASC
            """, nativeQuery = true)
    List<Object[]> findDailyCountByChannelId(@Param("channelId") Long channelId,
                                             @Param("startDate") Instant startDate);
}

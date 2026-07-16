package dev.bugi.sensor.sensordata.failure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface FailedReadingRepository extends JpaRepository<FailedReading, Long> {
    // 특정 장치의 특정 시각 이후 실패 적재 건수 — freshness 원인진단 신호로 사용.
    // (침묵 중에도 실패가 쌓이면 "데이터는 오는데 거부됨" = 규격변경 의심)
    int countByDeviceIdAndCreatedAtAfter(Long deviceId, Instant after);
}

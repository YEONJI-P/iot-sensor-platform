package dev.bugi.sensor.sensordata.dto;

import java.time.Instant;
import java.util.List;

/**
 * C2 응답 본문. 부분 실패(rejected)를 포함해도 batch 가 생성되면 200 이다.
 * 404(장치 없음)·422(전 채널 미지)에서는 batchId 가 null 이다.
 */
public record BatchIngestResponse(
        Long batchId,
        Long deviceId,
        String deviceCode,
        Instant observedAt,
        Instant receivedAt,
        int savedCount,
        List<RejectedReading> rejected
) {
}

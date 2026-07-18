package dev.bugi.sensor.sensordata.dto;

import java.time.Instant;
import java.util.List;

/**
 * C3 SSE payload(event: sensor-data). 한 batch 의 채널 판독을 한 묶음으로 전송한다.
 * 이 직렬화 형태(필드명)는 프론트 트랙의 유일한 의존이며 스냅숏 테스트로 고정한다.
 */
public record BatchSsePayload(
        Long batchId,
        Long deviceId,
        Instant observedAt,
        Instant receivedAt,
        List<Reading> readings
) {
    public record Reading(Long channelId, String channelCode, Double value) {
    }
}

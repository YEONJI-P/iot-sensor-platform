package dev.bugi.sensor.sensordata.dto;

import dev.bugi.sensor.sensordata.entity.SensorReading;

import java.time.Instant;

/**
 * 채널별 판독 조회 응답(C4 GET /channels/{id}/readings). 관측 시각은 batch 에서 온다.
 */
public record ReadingResponse(
        Long batchId,
        Double value,
        boolean anomaly,
        Instant observedAt,
        Instant receivedAt
) {
    public static ReadingResponse from(SensorReading reading, boolean anomaly) {
        return new ReadingResponse(
                reading.getBatch().getId(),
                reading.getValue(),
                anomaly,
                reading.getBatch().getObservedAt(),
                reading.getBatch().getReceivedAt());
    }
}

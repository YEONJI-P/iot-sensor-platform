package dev.bugi.sensor.alert.dto;

import dev.bugi.sensor.device.entity.SensorChannel;

/**
 * 알림 보강 대상의 최소 값 집합. LAZY 프록시 접근 없이 트랜잭션 밖 explain 호출에 필요한 값만 담는 프로젝션.
 *
 * 임계 alert 만 대상이다(channelId 로 최근 판독 윈도우를 로드한다). freshness alert 는 채널이 없어 제외된다.
 * explain 의 sensor_type ← quantityKind, unit ← channel.unit 로 매핑한다(explain 스키마 무변경).
 */
public record EnrichTarget(
        Long alertId,
        Long channelId,
        String deviceName,
        String quantityKind,
        String unit,
        Double sensorValue,
        Double thresholdValue,
        SensorChannel.ThresholdDirection thresholdDirection,
        String message
) {
}

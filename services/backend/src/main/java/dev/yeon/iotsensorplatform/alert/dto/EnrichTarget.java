package dev.yeon.iotsensorplatform.alert.dto;

import dev.yeon.iotsensorplatform.device.entity.Device;

/**
 * 알림 보강 대상의 최소 값 집합. LAZY 프록시(Device) 접근 없이
 * 트랜잭션 밖에서 AX 호출에 필요한 값만 담는 프로젝션.
 */
public record EnrichTarget(
        Long alertId,
        String deviceName,
        Device.DeviceType sensorType,
        Double sensorValue,
        Double thresholdValue,
        String message
) {
}

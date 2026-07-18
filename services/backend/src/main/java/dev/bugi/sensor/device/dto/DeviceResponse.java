package dev.bugi.sensor.device.dto;

import dev.bugi.sensor.device.entity.Device;

/**
 * C5 장치 응답 = {id, code, name, location, expectedIntervalSeconds, zoneId, zoneName}.
 */
public record DeviceResponse(
        Long id,
        String code,
        String name,
        String location,
        Integer expectedIntervalSeconds,
        Long zoneId,
        String zoneName
) {
    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getCode(),
                device.getName(),
                device.getLocation(),
                device.getExpectedIntervalSeconds(),
                device.getZone() != null ? device.getZone().getId() : null,
                device.getZone() != null ? device.getZone().getName() : null
        );
    }
}

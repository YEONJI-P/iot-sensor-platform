package dev.bugi.sensor.device.dto;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.factory.entity.Zone;

/**
 * C4 채널 응답. 대시보드 드롭다운·조회에 필요한 device·zone 정보를 함께 담는다.
 */
public record ChannelResponse(
        Long id,
        String code,
        String unit,
        String quantityKind,
        Double thresholdValue,
        SensorChannel.ThresholdDirection thresholdDirection,
        Long deviceId,
        String deviceCode,
        String deviceName,
        Long zoneId,
        String zoneName
) {
    public static ChannelResponse from(SensorChannel channel) {
        Device device = channel.getDevice();
        Zone zone = device != null ? device.getZone() : null;
        return new ChannelResponse(
                channel.getId(),
                channel.getCode(),
                channel.getUnit(),
                channel.getQuantityKind(),
                channel.getThresholdValue(),
                channel.getThresholdDirection(),
                device != null ? device.getId() : null,
                device != null ? device.getCode() : null,
                device != null ? device.getName() : null,
                zone != null ? zone.getId() : null,
                zone != null ? zone.getName() : null
        );
    }
}

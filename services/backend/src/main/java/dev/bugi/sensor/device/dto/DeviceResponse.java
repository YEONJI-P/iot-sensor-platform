package dev.bugi.sensor.device.dto;

import dev.bugi.sensor.device.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private Long id;
    private String name;
    private Device.DeviceType deviceType;
    private String location;
    private Double thresholdValue;
    private Long zoneId;
    private String zoneName;

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getType(),
                device.getLocation(),
                device.getThresholdValue(),
                device.getZone() != null ? device.getZone().getId() : null,
                device.getZone() != null ? device.getZone().getName() : null
        );
    }
}

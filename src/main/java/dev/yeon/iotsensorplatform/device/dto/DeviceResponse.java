package dev.yeon.iotsensorplatform.device.dto;

import dev.yeon.iotsensorplatform.device.entity.Device;
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
    private Long userId;

    public static DeviceResponse from(Device device){
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getType(),
                device.getLocation(),
                device.getThresholdValue(),
                device.getUser().getId()
        );
    }

}

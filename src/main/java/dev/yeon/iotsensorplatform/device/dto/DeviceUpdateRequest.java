package dev.yeon.iotsensorplatform.device.dto;

import dev.yeon.iotsensorplatform.device.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdateRequest {
    private String name;
    private Device.DeviceType type;
    private String location;
    private Double thresholdValue;
}

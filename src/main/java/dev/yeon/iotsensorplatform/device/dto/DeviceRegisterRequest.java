package dev.yeon.iotsensorplatform.device.dto;

import dev.yeon.iotsensorplatform.device.entity.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegisterRequest {
    @NotBlank
    private String name;
    @NotNull
    private Device.DeviceType type;
    private String location;
    private Double thresholdValue;
    @NotNull
    private Long groupId;
}

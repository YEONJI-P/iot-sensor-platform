package dev.bugi.sensor.device.dto;

import dev.bugi.sensor.device.entity.SensorChannel;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C4 채널 등록(POST /devices/{deviceId}/channels). code 는 device 안에서 유일해야 한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreateRequest {
    @NotBlank
    private String code;
    private String unit;
    private String quantityKind;
    private Double thresholdValue;
    private SensorChannel.ThresholdDirection thresholdDirection;
}

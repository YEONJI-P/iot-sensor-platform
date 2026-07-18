package dev.bugi.sensor.device.dto;

import dev.bugi.sensor.device.entity.SensorChannel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C4 채널 수정(PUT /channels/{id}). 주로 임계값·방향을 조정한다. code 는 수정하지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelUpdateRequest {
    private String unit;
    private String quantityKind;
    private Double thresholdValue;
    private SensorChannel.ThresholdDirection thresholdDirection;
}

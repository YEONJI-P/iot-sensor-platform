package dev.bugi.sensor.device.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C5 장치 수정. code 는 안정적 식별자라 수정 대상에서 제외한다(노드 설정만 수정).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdateRequest {
    private String name;
    private String location;
    private Integer expectedIntervalSeconds;
}

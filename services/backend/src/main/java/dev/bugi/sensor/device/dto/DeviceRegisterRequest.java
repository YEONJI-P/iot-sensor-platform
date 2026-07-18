package dev.bugi.sensor.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C5 장치 등록. 측정 종류·임계값은 채널 경계로 옮겨 제거하고, 물리 노드 식별자 code 를 추가했다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegisterRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String location;
    private Integer expectedIntervalSeconds;
    @NotNull
    private Long zoneId;
}

package dev.yeon.iotsensorplatform.sensordata.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataRequest {

    @NotNull
    private Long deviceId;
    @NotNull
    private Double value;


}

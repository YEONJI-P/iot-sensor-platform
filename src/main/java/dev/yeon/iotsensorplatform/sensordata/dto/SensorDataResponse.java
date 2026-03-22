package dev.yeon.iotsensorplatform.sensordata.dto;

import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataResponse {
    private Long id;
    private Long deviceId;
    private Double value;
    private LocalDateTime recordedAt;

    public static SensorDataResponse from(SensorData sensorData){
        return new SensorDataResponse(
                sensorData.getId(),
                sensorData.getDevice().getId(),
                sensorData.getValue(),
                sensorData.getRecordedAt()
        );
    }

}

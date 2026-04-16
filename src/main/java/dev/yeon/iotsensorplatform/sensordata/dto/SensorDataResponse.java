package dev.yeon.iotsensorplatform.sensordata.dto;

import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
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

    public static SensorDataResponse from(SensorData data) {
        return new SensorDataResponse(
                data.getId(),
                data.getDevice().getId(),
                data.getValue(),
                data.getRecordedAt()
        );
    }
}

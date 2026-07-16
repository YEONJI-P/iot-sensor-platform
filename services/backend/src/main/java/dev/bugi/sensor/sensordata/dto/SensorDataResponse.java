package dev.bugi.sensor.sensordata.dto;

import dev.bugi.sensor.sensordata.entity.SensorData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataResponse {
    private Long id;
    private Long deviceId;
    private Double value;
    private Instant recordedAt;

    public static SensorDataResponse from(SensorData data) {
        return new SensorDataResponse(
                data.getId(),
                data.getDevice().getId(),
                data.getValue(),
                data.getRecordedAt()
        );
    }
}

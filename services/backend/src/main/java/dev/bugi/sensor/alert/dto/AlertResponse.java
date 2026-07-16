package dev.bugi.sensor.alert.dto;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {
    private Long id;
    private Long deviceId;
    private Double sensorValue;
    private Double thresholdValue;
    private String message;
    private AlertSeverity severity;
    private String evidence;
    private String recommendation;
    private Instant createdAt;

    public static AlertResponse from(Alert alert){
        return new AlertResponse(
                alert.getId(),
                alert.getDevice().getId(),
                alert.getSensorValue(),
                alert.getThresholdValue(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getEvidence(),
                alert.getRecommendation(),
                alert.getCreatedAt()
        );
    }
}

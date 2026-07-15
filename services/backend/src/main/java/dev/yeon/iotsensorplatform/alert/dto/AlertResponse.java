package dev.yeon.iotsensorplatform.alert.dto;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.entity.AlertSeverity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

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

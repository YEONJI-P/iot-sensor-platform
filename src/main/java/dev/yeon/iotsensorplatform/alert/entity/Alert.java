package dev.yeon.iotsensorplatform.alert.entity;

import dev.yeon.iotsensorplatform.device.entity.Device;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;
    // SensorData FK 대신 값 직접 저장 (스냅샷 방식 - SensorData 삭제돼도 이력 보존)
    private Double sensorValue;
    private Double thresholdValue;
    private String message;
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Alert(Device device, Double sensorValue, Double thresholdValue, String message) {
        this.device = device;
        this.sensorValue = sensorValue;
        this.thresholdValue = thresholdValue;
        this.message = message;
    }
}
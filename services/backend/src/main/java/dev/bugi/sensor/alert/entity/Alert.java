package dev.bugi.sensor.alert.entity;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.sensordata.entity.MeasurementBatch;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 임계 alert 는 device·channel·batch 를 모두 세팅한다.
    // freshness(수신 끊김) alert 는 채널·batch 가 없어 device 만 세팅한다(device_id nullable).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private SensorChannel channel;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private MeasurementBatch batch;
    // reading FK 대신 값 직접 저장 (스냅샷 방식 - reading 삭제돼도 이력 보존)
    private Double sensorValue;
    private Double thresholdValue;
    private String message;
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    private String evidence;
    private String recommendation;
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    // evidence/recommendation 이 enrich()로 나중에 채워질 때 갱신된다.
    @LastModifiedDate
    private Instant updatedAt;

    @Builder
    public Alert(Device device, SensorChannel channel, MeasurementBatch batch,
                 Double sensorValue, Double thresholdValue, String message,
                 AlertSeverity severity, String evidence, String recommendation) {
        this.device = device;
        this.channel = channel;
        this.batch = batch;
        this.sensorValue = sensorValue;
        this.thresholdValue = thresholdValue;
        this.message = message;
        this.severity = severity;
        this.evidence = evidence;
        this.recommendation = recommendation;
    }

    public void enrich(String evidence, String recommendation) {
        this.evidence = evidence;
        this.recommendation = recommendation;
    }
}
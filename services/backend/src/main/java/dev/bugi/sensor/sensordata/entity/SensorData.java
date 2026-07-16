package dev.bugi.sensor.sensordata.entity;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "sensor_data", indexes = @Index(name = "idx_sensor_device_recorded", columnList = "device_id, recorded_at"))
@EntityListeners(AuditingEntityListener.class)
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    private Double value;

    @CreatedDate
    @Column(updatable = false)
    private Instant recordedAt;

    @Builder
    public SensorData(Device device, Double value) {
        this.device = device;
        this.value = value;
    }

}

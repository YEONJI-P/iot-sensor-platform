package dev.bugi.sensor.device.entity;

import dev.bugi.sensor.factory.entity.Zone;
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
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    private String name;

    @Enumerated(EnumType.STRING)
    private DeviceType type;

    private String location;

    private Double thresholdValue;

    private Integer expectedIntervalSeconds;

    private Instant lastSeenAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Builder
    public Device(Zone zone, String name, DeviceType type, String location, Double thresholdValue, Integer expectedIntervalSeconds) {
        this.zone = zone;
        this.name = name;
        this.type = type;
        this.location = location;
        this.thresholdValue = thresholdValue;
        this.expectedIntervalSeconds = expectedIntervalSeconds;
    }

    public void update(String name, DeviceType type, String location, Double thresholdValue) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.thresholdValue = thresholdValue;
    }

    public void markSeen(Instant at) {
        this.lastSeenAt = at;
    }

    public enum DeviceType {
        TEMPERATURE, PRESSURE, CURRENT, POWER, ACCELERATION
    }
}

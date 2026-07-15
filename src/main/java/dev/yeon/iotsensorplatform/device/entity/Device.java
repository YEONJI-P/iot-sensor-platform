package dev.yeon.iotsensorplatform.device.entity;

import dev.yeon.iotsensorplatform.factory.entity.Zone;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
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

    private LocalDateTime lastSeenAt;

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

    public void markSeen(LocalDateTime at) {
        this.lastSeenAt = at;
    }

    public enum DeviceType {
        TEMPERATURE, PRESSURE, CURRENT, POWER, ACCELERATION
    }
}

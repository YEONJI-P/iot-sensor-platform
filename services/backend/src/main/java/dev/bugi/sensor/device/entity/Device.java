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

    // 런타임 상태(수신 하트비트 lastSeenAt, 알람 상태 inAlarm/lastAlertAt)는
    // 설정 감사(updatedAt)를 오염시키지 않도록 DeviceStatus 로 분리했다.

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

    public enum DeviceType {
        TEMPERATURE("°C"),
        PRESSURE("kPa"),
        CURRENT("A"),
        POWER("kW"),
        ACCELERATION("m/s²");

        private final String unit;

        DeviceType(String unit) {
            this.unit = unit;
        }

        public String getUnit() {
            return unit;
        }
    }
}

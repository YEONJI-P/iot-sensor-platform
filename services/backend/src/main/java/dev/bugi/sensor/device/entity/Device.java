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

    // 수신 하트비트(lastSeenAt)는 설정 감사를 오염시키지 않도록 DeviceStatus 로 분리했다.

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // 알람 상태(엣지 트리거 쿨다운). 초과 진입 시 true, 재발화 방지 여유 아래로 복귀 시 false.
    // 매 판독마다 Alert를 만드는 스팸을 막고, 상태가 바뀌는 순간에만 발화한다.
    private boolean inAlarm = false;

    // 마지막 발화 시각(UTC). 신규 코드 → timestamptz + Instant + 주입 Clock 규칙 준수.
    private Instant lastAlertAt;

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

    /** 임계 초과 진입: 알람 상태로 전환하고 발화 시각을 기록한다. */
    public void enterAlarm(Instant at) {
        this.inAlarm = true;
        this.lastAlertAt = at;
    }

    /** 임계값 아래 여유 구간까지 복귀: 알람 해제(알림은 만들지 않는다). */
    public void clearAlarm() {
        this.inAlarm = false;
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

package dev.bugi.sensor.device.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 물리 Device 아래의 측정 채널. 임계값·임계 방향은 측정 종류마다 다르므로
 * Device(노드 설정)가 아니라 채널 경계에 둔다.
 *
 * 채널은 설정 엔티티라 created/updated 를 감사한다. 런타임 알람 상태는
 * ChannelStatus 로 분리해 updatedAt(설정 감사)을 오염시키지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "sensor_channel",
        uniqueConstraints = @UniqueConstraint(name = "uk_sensor_channel_device_code", columnNames = {"device_id", "code"})
)
@EntityListeners(AuditingEntityListener.class)
public class SensorChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    // 물리 Device 안에서 채널을 가리키는 코드(예: s4, S1_OutputPower). (device, code) 유일.
    private String code;

    // 표시 단위(예: °R, psia, kW). enum 이 아닌 자유 문자열.
    private String unit;

    // 측정 종류(예: temperature, pressure). 소문자 문자열, enum 아님(값 검증은 하지 않음).
    private String quantityKind;

    private Double thresholdValue;

    @Enumerated(EnumType.STRING)
    private ThresholdDirection thresholdDirection;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Builder
    public SensorChannel(Device device, String code, String unit, String quantityKind,
                         Double thresholdValue, ThresholdDirection thresholdDirection) {
        this.device = device;
        this.code = code;
        this.unit = unit;
        this.quantityKind = quantityKind;
        this.thresholdValue = thresholdValue;
        this.thresholdDirection = thresholdDirection;
    }

    public void update(String unit, String quantityKind, Double thresholdValue, ThresholdDirection thresholdDirection) {
        this.unit = unit;
        this.quantityKind = quantityKind;
        this.thresholdValue = thresholdValue;
        this.thresholdDirection = thresholdDirection;
    }

    // 임계 초과 방향. 데모는 전부 ABOVE(값이 임계값을 위로 넘으면 이상).
    public enum ThresholdDirection {
        ABOVE,
        BELOW
    }
}

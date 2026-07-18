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

/**
 * 물리 노드(측정 장치 자체). 측정 종류·임계값 같은 채널 단위 설정은 SensorChannel 로 옮겼다.
 * Device 는 노드 식별(code)·위치·기대 수신 주기 같은 노드 경계 설정만 가진다.
 */
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

    // 물리 노드를 가리키는 안정적 식별자. 수신 API 는 이 code 로 장치를 찾는다. (UK)
    private String code;

    private String name;

    private String location;

    private Integer expectedIntervalSeconds;

    // 런타임 상태(수신 하트비트 lastSeenAt)는 설정 감사(updatedAt)를 오염시키지 않도록
    // DeviceStatus 로 분리했다. 알람 상태는 채널 경계인 ChannelStatus 로 옮겼다.

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Builder
    public Device(Zone zone, String code, String name, String location, Integer expectedIntervalSeconds) {
        this.zone = zone;
        this.code = code;
        this.name = name;
        this.location = location;
        this.expectedIntervalSeconds = expectedIntervalSeconds;
    }

    public void update(String name, String location, Integer expectedIntervalSeconds) {
        this.name = name;
        this.location = location;
        this.expectedIntervalSeconds = expectedIntervalSeconds;
    }
}

package dev.bugi.sensor.device.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 장치의 런타임 텔레메트리(수신 하트비트). 설정 엔티티인 Device 와 분리해 둔다.
 *
 * 분리 이유: lastSeenAt 은 센서 수신마다 갱신되는 고빈도 값이라, Device 에 두면
 * (1) 매 수신이 Device 행을 dirty 시켜 @LastModifiedDate(updatedAt) 를 오염시키고
 *     — updatedAt 은 이름·임계값 같은 "설정 변경"만 감사해야 한다 —
 * (2) 설정 행이 매 수신마다 다시 쓰여 불필요하게 뜨거워진다.
 *
 * device_id 를 Device 와 공유 PK(@MapsId)로 쓴다(장치당 1행).
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "device_status")
public class DeviceStatus {

    @Id
    private Long deviceId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "device_id")
    private Device device;

    private Instant lastSeenAt;

    // 알람 상태(엣지 트리거 쿨다운). 초과 진입 시 true, 재발화 방지 여유 아래로 복귀 시 false.
    // 매 판독마다 Alert를 만드는 스팸을 막고, 상태가 바뀌는 순간에만 발화한다.
    private boolean inAlarm = false;

    // 마지막 발화 시각(UTC).
    private Instant lastAlertAt;

    public DeviceStatus(Device device, Instant lastSeenAt) {
        this.device = device;
        this.lastSeenAt = lastSeenAt;
    }

    public void markSeen(Instant at) {
        this.lastSeenAt = at;
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
}

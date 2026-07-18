package dev.bugi.sensor.device.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 채널의 런타임 알람 상태(엣지 트리거 쿨다운). 설정 엔티티인 SensorChannel 과 분리해 둔다.
 *
 * 분리 이유는 DeviceStatus 와 같다: 매 판독마다 바뀌는 알람 상태가 SensorChannel 에 있으면
 * @LastModifiedDate(updatedAt, "설정 변경" 감사)를 오염시키고 설정 행이 뜨거워진다.
 *
 * channel_id 를 SensorChannel 과 공유 PK(@MapsId)로 쓴다(채널당 1행).
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "channel_status")
public class ChannelStatus {

    @Id
    private Long channelId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "channel_id")
    private SensorChannel channel;

    // 알람 상태(엣지 트리거 쿨다운). 임계 초과 진입 시 true, 재발화 방지 여유 아래로 복귀 시 false.
    private boolean inAlarm = false;

    // 마지막 발화 시각(UTC).
    private Instant lastAlertAt;

    public ChannelStatus(SensorChannel channel) {
        this.channel = channel;
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

package dev.bugi.sensor.sensordata.failure;

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
@EntityListeners(AuditingEntityListener.class)
public class FailedReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 요청에 담긴 deviceId 값만 저장한다. FK가 아니며 존재하지 않는 장치일 수 있다.
    // (구 scalar API 잔재 — 새 batch API 는 아래 deviceCode/channelCode 로 실패를 기록한다.)
    private Long deviceId;

    private Double value;

    // 새 batch API 의 문자열 식별자. deviceCode 미존재(404) 요약, 미지 채널(UNKNOWN_CHANNEL) 등에 쓴다.
    private String deviceCode;

    private String channelCode;

    // 실패 사유 문자열 (예: "DEVICE_NOT_FOUND", "UNKNOWN_CHANNEL", "NULL_VALUE")
    private String reason;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @Builder
    public FailedReading(Long deviceId, Double value, String deviceCode, String channelCode, String reason) {
        this.deviceId = deviceId;
        this.value = value;
        this.deviceCode = deviceCode;
        this.channelCode = channelCode;
        this.reason = reason;
    }

}

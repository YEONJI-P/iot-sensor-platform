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
    private Long deviceId;

    private Double value;

    // 실패 사유 문자열 (예: "DEVICE_NOT_FOUND")
    private String reason;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @Builder
    public FailedReading(Long deviceId, Double value, String reason) {
        this.deviceId = deviceId;
        this.value = value;
        this.reason = reason;
    }

}

package dev.bugi.sensor.sensordata.entity;

import dev.bugi.sensor.device.entity.SensorChannel;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * batch × channel 교차의 단일 값. 감사 컬럼이 없다 — 관측 시각은 batch(observed_at)에서 온다.
 * (batch, channel) 은 유일하다: 한 batch 안에서 같은 채널이 두 번 오지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "sensor_reading",
        uniqueConstraints = @UniqueConstraint(name = "uk_sensor_reading_batch_channel", columnNames = {"batch_id", "channel_id"}),
        indexes = @Index(name = "idx_sensor_reading_channel", columnList = "channel_id")
)
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private MeasurementBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private SensorChannel channel;

    private Double value;

    @Builder
    public SensorReading(MeasurementBatch batch, SensorChannel channel, Double value) {
        this.batch = batch;
        this.channel = channel;
        this.value = value;
    }
}

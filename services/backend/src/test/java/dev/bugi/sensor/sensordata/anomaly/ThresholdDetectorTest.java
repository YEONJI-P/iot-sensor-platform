package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdDetectorTest {

    private final ThresholdDetector detector = new ThresholdDetector();

    private SensorChannel channel(Double threshold, ThresholdDirection direction) {
        return SensorChannel.builder()
                .code("s4").unit("°R").quantityKind("temperature")
                .thresholdValue(threshold).thresholdDirection(direction)
                .build();
    }

    @Test
    @DisplayName("ABOVE: 값이 임계값을 초과하면 이상으로 판정한다")
    void above_whenValueExceedsThreshold_returnsTrue() {
        assertThat(detector.isAnomaly(channel(80.0, ThresholdDirection.ABOVE), 90.0)).isTrue();
    }

    @Test
    @DisplayName("ABOVE: 값이 임계값 이하이면 정상으로 판정한다(경계 포함)")
    void above_whenValueAtOrBelowThreshold_returnsFalse() {
        assertThat(detector.isAnomaly(channel(80.0, ThresholdDirection.ABOVE), 80.0)).isFalse();
        assertThat(detector.isAnomaly(channel(80.0, ThresholdDirection.ABOVE), 70.0)).isFalse();
    }

    @Test
    @DisplayName("BELOW: 값이 임계값 미만이면 이상으로 판정한다(최소 구현)")
    void below_whenValueUnderThreshold_returnsTrue() {
        assertThat(detector.isAnomaly(channel(80.0, ThresholdDirection.BELOW), 70.0)).isTrue();
        assertThat(detector.isAnomaly(channel(80.0, ThresholdDirection.BELOW), 80.0)).isFalse();
    }

    @Test
    @DisplayName("임계값이 null이면 방향과 무관하게 정상으로 판정한다")
    void whenThresholdIsNull_returnsFalse() {
        assertThat(detector.isAnomaly(channel(null, ThresholdDirection.ABOVE), 90.0)).isFalse();
        assertThat(detector.isAnomaly(channel(null, ThresholdDirection.BELOW), 10.0)).isFalse();
    }
}

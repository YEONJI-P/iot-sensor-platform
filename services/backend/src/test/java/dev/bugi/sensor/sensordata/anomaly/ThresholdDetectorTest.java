package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.Device;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdDetectorTest {

    private final ThresholdDetector detector = new ThresholdDetector();

    @Test
    @DisplayName("값이 임계값을 초과하면 이상으로 판정한다")
    void isAnomaly_whenValueExceedsThreshold_returnsTrue() {
        Device device = Device.builder()
                .name("온도센서")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장 A")
                .thresholdValue(80.0)
                .build();

        assertThat(detector.isAnomaly(device, 90.0)).isTrue();
    }

    @Test
    @DisplayName("값이 임계값 이하이면 정상으로 판정한다")
    void isAnomaly_whenValueBelowThreshold_returnsFalse() {
        Device device = Device.builder()
                .name("온도센서")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장 A")
                .thresholdValue(80.0)
                .build();

        assertThat(detector.isAnomaly(device, 70.0)).isFalse();
    }

    @Test
    @DisplayName("임계값이 null이면 정상으로 판정한다")
    void isAnomaly_whenThresholdIsNull_returnsFalse() {
        Device device = Device.builder()
                .name("온도센서")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장 A")
                .thresholdValue(null)
                .build();

        assertThat(detector.isAnomaly(device, 90.0)).isFalse();
    }
}

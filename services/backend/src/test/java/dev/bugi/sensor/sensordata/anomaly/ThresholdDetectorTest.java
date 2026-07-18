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
    @DisplayName("임계 방향이 null인 기존 채널은 ABOVE로 해석한다")
    void nullDirection_fallsBackToAbove() {
        assertThat(detector.isAnomaly(channel(80.0, null), 80.01)).isTrue();
        assertThat(detector.isAnomaly(channel(80.0, null), 80.0)).isFalse();
    }

    @Test
    @DisplayName("ABS_ABOVE: 양·음 값의 절댓값이 임계값을 초과하면 이상으로 판정한다")
    void absoluteAbove_detectsPositiveAndNegativeAnomalies() {
        SensorChannel channel = channel(100.0, ThresholdDirection.ABS_ABOVE);

        assertThat(detector.isAnomaly(channel, 100.01)).isTrue();
        assertThat(detector.isAnomaly(channel, -100.01)).isTrue();
        assertThat(detector.isAnomaly(channel, 100.0)).isFalse();
        assertThat(detector.isAnomaly(channel, -100.0)).isFalse();
    }

    @Test
    @DisplayName("임계값이 null이면 방향과 무관하게 정상으로 판정한다")
    void whenThresholdIsNull_returnsFalse() {
        assertThat(detector.isAnomaly(channel(null, ThresholdDirection.ABOVE), 90.0)).isFalse();
        assertThat(detector.isAnomaly(channel(null, ThresholdDirection.BELOW), 10.0)).isFalse();
    }

    @Test
    @DisplayName("해제 경계는 ABOVE 0.97 미만, BELOW 1.03 초과다")
    void releaseBoundary_usesDirectionFormula() {
        assertThat(detector.isReleased(channel(100.0, ThresholdDirection.ABOVE), 97.0, 0.97)).isFalse();
        assertThat(detector.isReleased(channel(100.0, ThresholdDirection.ABOVE), 96.99, 0.97)).isTrue();
        assertThat(detector.isReleased(channel(100.0, ThresholdDirection.BELOW), 103.0, 0.97)).isFalse();
        assertThat(detector.isReleased(channel(100.0, ThresholdDirection.BELOW), 103.01, 0.97)).isTrue();
    }

    @Test
    @DisplayName("ABS_ABOVE 해제는 양·음 값 모두 절댓값 0.97배 미만에서만 성립한다")
    void absoluteAbove_releaseUsesAbsoluteValue() {
        SensorChannel channel = channel(100.0, ThresholdDirection.ABS_ABOVE);

        assertThat(detector.isReleased(channel, 97.0, 0.97)).isFalse();
        assertThat(detector.isReleased(channel, -97.0, 0.97)).isFalse();
        assertThat(detector.isReleased(channel, 96.99, 0.97)).isTrue();
        assertThat(detector.isReleased(channel, -96.99, 0.97)).isTrue();
    }

    @Test
    @DisplayName("CRITICAL 경계는 ABOVE 1.1 초과, BELOW 0.9 미만이다")
    void criticalBoundary_usesDirectionFormula() {
        assertThat(detector.isCritical(channel(100.0, ThresholdDirection.ABOVE), 110.0, 1.1)).isFalse();
        assertThat(detector.isCritical(channel(100.0, ThresholdDirection.ABOVE), 110.01, 1.1)).isTrue();
        assertThat(detector.isCritical(channel(100.0, ThresholdDirection.BELOW), 90.0, 1.1)).isFalse();
        assertThat(detector.isCritical(channel(100.0, ThresholdDirection.BELOW), 89.99, 1.1)).isTrue();
    }

    @Test
    @DisplayName("음수 임계값도 절댓값 기반 여유 폭으로 해제·CRITICAL 경계를 유지한다")
    void negativeThreshold_usesAdditiveAbsoluteMargin() {
        SensorChannel above = channel(-10.0, ThresholdDirection.ABOVE);
        SensorChannel below = channel(-10.0, ThresholdDirection.BELOW);

        assertThat(detector.isReleased(above, -11.0, 0.9)).isFalse();
        assertThat(detector.isReleased(above, -11.01, 0.9)).isTrue();
        assertThat(detector.isCritical(above, -9.0, 1.1)).isFalse();
        assertThat(detector.isCritical(above, -8.99, 1.1)).isTrue();
        assertThat(detector.isReleased(below, -9.0, 0.9)).isFalse();
        assertThat(detector.isReleased(below, -8.99, 0.9)).isTrue();
        assertThat(detector.isCritical(below, -11.0, 1.1)).isFalse();
        assertThat(detector.isCritical(below, -11.01, 1.1)).isTrue();
    }

    @Test
    @DisplayName("ABS_ABOVE CRITICAL은 양·음 값 모두 절댓값 1.1배 초과에서만 성립한다")
    void absoluteAbove_criticalUsesAbsoluteValue() {
        SensorChannel channel = channel(100.0, ThresholdDirection.ABS_ABOVE);

        assertThat(detector.isCritical(channel, 110.0, 1.1)).isFalse();
        assertThat(detector.isCritical(channel, -110.0, 1.1)).isFalse();
        assertThat(detector.isCritical(channel, 110.01, 1.1)).isTrue();
        assertThat(detector.isCritical(channel, -110.01, 1.1)).isTrue();
    }

    @Test
    @DisplayName("임계값이 null이면 해제 가능하고 CRITICAL 기본값을 사용한다")
    void nullThreshold_usesSafeTransitionDefaults() {
        for (ThresholdDirection direction : ThresholdDirection.values()) {
            SensorChannel channel = channel(null, direction);
            assertThat(detector.isAnomaly(channel, -1_000_000.0)).isFalse();
            assertThat(detector.isAnomaly(channel, 1_000_000.0)).isFalse();
            assertThat(detector.isReleased(channel, 100.0, 0.97)).isTrue();
            assertThat(detector.isCritical(channel, 100.0, 1.1)).isTrue();
        }
    }
}

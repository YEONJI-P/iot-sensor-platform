package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import org.springframework.stereotype.Component;

@Component
public class ThresholdDetector implements AnomalyDetector {

    /** 임계 방향별 이상·해제·심각도 경계 수식의 단일 구현. */
    @Override
    public boolean isAnomaly(SensorChannel channel, double value) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return false;
        }
        return switch (directionOf(channel)) {
            case BELOW -> value < threshold;
            case ABS_ABOVE -> Math.abs(value) > threshold;
            case ABOVE -> value > threshold;
        };
    }

    @Override
    public boolean isReleased(SensorChannel channel, double value, double ratio) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return true;
        }
        return switch (directionOf(channel)) {
            case BELOW -> value > threshold * (2 - ratio);
            case ABS_ABOVE -> Math.abs(value) < threshold * ratio;
            case ABOVE -> value < threshold * ratio;
        };
    }

    @Override
    public boolean isCritical(SensorChannel channel, double value, double ratio) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return true;
        }
        return switch (directionOf(channel)) {
            case BELOW -> value < threshold * (2 - ratio);
            case ABS_ABOVE -> Math.abs(value) > threshold * ratio;
            case ABOVE -> value > threshold * ratio;
        };
    }

    /** 기존 nullable direction 계약은 ABOVE 로 해석한다. */
    private ThresholdDirection directionOf(SensorChannel channel) {
        return channel.getThresholdDirection() == null
                ? ThresholdDirection.ABOVE
                : channel.getThresholdDirection();
    }
}

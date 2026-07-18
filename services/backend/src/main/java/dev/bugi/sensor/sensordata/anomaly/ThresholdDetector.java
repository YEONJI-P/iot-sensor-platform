package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import org.springframework.stereotype.Component;

@Component
public class ThresholdDetector implements AnomalyDetector {

    /** 임계 방향별 이상·해제·심각도 경계 수식의 단일 구현. 데모 채널은 전부 ABOVE. */
    @Override
    public boolean isAnomaly(SensorChannel channel, double value) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return false;
        }
        return channel.getThresholdDirection() == ThresholdDirection.BELOW
                ? value < threshold
                : value > threshold;
    }

    @Override
    public boolean isReleased(SensorChannel channel, double value, double ratio) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return true;
        }
        return channel.getThresholdDirection() == ThresholdDirection.BELOW
                ? value > threshold * (2 - ratio)
                : value < threshold * ratio;
    }

    @Override
    public boolean isCritical(SensorChannel channel, double value, double ratio) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return true;
        }
        return channel.getThresholdDirection() == ThresholdDirection.BELOW
                ? value < threshold * (2 - ratio)
                : value > threshold * ratio;
    }
}

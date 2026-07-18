package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import org.springframework.stereotype.Component;

@Component
public class ThresholdDetector implements AnomalyDetector {

    /**
     * 임계 초과(이상) 판정. 임계값이 없으면 판정하지 않는다(정상 취급).
     *
     * 임계 방향에 따라 초과/미달을 이상으로 본다. 데모 채널은 전부 ABOVE.
     * 해제(히스테리시스) 밴드는 판정이 아니라 수신 서비스가 상태 전이로 관리하며, 방향별 수식은:
     *   ABOVE: 이상 = value > threshold,  해제 = value < threshold * 0.97
     *   BELOW: 이상 = value < threshold,  해제 = value > threshold * 1.03  (최소 구현, 데모 미사용)
     */
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
}

package dev.yeon.iotsensorplatform.sensordata.anomaly;

import dev.yeon.iotsensorplatform.device.entity.Device;
import org.springframework.stereotype.Component;

@Component
public class ThresholdDetector implements AnomalyDetector {

    @Override
    public boolean isAnomaly(Device device, double value) {
        return device.getThresholdValue() != null && value > device.getThresholdValue();
    }
}

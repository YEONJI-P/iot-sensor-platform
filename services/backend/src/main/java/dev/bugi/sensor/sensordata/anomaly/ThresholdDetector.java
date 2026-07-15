package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.Device;
import org.springframework.stereotype.Component;

@Component
public class ThresholdDetector implements AnomalyDetector {

    @Override
    public boolean isAnomaly(Device device, double value) {
        return device.getThresholdValue() != null && value > device.getThresholdValue();
    }
}

package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.Device;

public interface AnomalyDetector {

    boolean isAnomaly(Device device, double value);
}

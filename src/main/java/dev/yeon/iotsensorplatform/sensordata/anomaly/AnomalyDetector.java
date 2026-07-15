package dev.yeon.iotsensorplatform.sensordata.anomaly;

import dev.yeon.iotsensorplatform.device.entity.Device;

public interface AnomalyDetector {

    boolean isAnomaly(Device device, double value);
}

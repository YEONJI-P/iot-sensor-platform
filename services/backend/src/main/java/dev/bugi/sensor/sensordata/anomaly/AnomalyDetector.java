package dev.bugi.sensor.sensordata.anomaly;

import dev.bugi.sensor.device.entity.SensorChannel;

public interface AnomalyDetector {

    // 임계 판정은 채널 경계에서 한다(임계값·방향은 SensorChannel 이 가진다).
    boolean isAnomaly(SensorChannel channel, double value);

    // 히스테리시스 해제 여부. ratio 는 ABOVE 기준 경계 비율(예: 0.97)이다.
    boolean isReleased(SensorChannel channel, double value, double ratio);

    // 발화 값이 CRITICAL 구간인지 여부. ratio 는 ABOVE 기준 경계 비율(예: 1.1)이다.
    boolean isCritical(SensorChannel channel, double value, double ratio);
}

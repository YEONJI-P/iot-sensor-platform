package dev.yeon.iotsensorplatform.ax.dto;

import java.util.List;

/**
 * AX 이상 근거·권고 요청. Jackson 기본 camelCase 직렬화가 Python 스키마와 맞는다.
 */
public record AnomalyExplainRequest(
        String deviceName,
        String sensorType,
        double value,
        Double threshold,
        String message,
        List<Double> recentValues
) {
}

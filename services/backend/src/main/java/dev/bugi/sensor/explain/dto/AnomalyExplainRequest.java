package dev.bugi.sensor.explain.dto;

import dev.bugi.sensor.device.entity.SensorChannel;

import java.util.List;

/**
 * explain 이상 근거·권고 요청. Jackson 기본 camelCase 직렬화가 Python 스키마와 맞는다.
 */
public record AnomalyExplainRequest(
        String deviceName,
        String sensorType,
        String unit,
        double value,
        Double threshold,
        SensorChannel.ThresholdDirection thresholdDirection,
        String message,
        List<Double> recentValues,
        // 최근 윈도우에서 규칙으로 계산한 파생 지표(설명용). 탐지엔 쓰지 않는다.
        // Spring이 값을 확정해 넘기고 Python은 서술만 한다. 데이터 부족 시 null.
        Double breachRate,   // 윈도우 내 방향별 임계 이탈 비율(0~1)
        Double trend,        // 추세(후반 평균 - 전반 평균). 양수면 상승 중
        Double volatility    // 변동성(표준편차)
) {
}

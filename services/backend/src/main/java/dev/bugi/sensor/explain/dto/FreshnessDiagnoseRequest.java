package dev.bugi.sensor.explain.dto;

/** explain 데이터 끊김 원인 진단 요청. */
public record FreshnessDiagnoseRequest(
        String deviceName,
        int expectedIntervalSeconds,
        String lastSeenAt,
        Integer elapsedSeconds,
        Integer failedReadingRecentCount
) {
}

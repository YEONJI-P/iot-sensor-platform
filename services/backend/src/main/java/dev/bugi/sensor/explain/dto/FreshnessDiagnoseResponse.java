package dev.bugi.sensor.explain.dto;

/** explain 데이터 끊김 원인 진단 응답. */
public record FreshnessDiagnoseResponse(
        String cause,
        String report,
        String model
) {
}

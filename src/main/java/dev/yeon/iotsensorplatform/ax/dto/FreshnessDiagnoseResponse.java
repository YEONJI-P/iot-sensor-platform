package dev.yeon.iotsensorplatform.ax.dto;

/** AX 데이터 끊김 원인 진단 응답. */
public record FreshnessDiagnoseResponse(
        String cause,
        String report,
        String model
) {
}

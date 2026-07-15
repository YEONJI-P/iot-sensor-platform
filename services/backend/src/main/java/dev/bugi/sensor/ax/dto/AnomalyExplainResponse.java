package dev.bugi.sensor.ax.dto;

/** AX 이상 근거·권고 응답. Alert의 evidence/recommendation에 채워진다. */
public record AnomalyExplainResponse(
        String evidence,
        String recommendation,
        String severity,
        String model
) {
}

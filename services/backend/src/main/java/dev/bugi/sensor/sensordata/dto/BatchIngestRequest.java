package dev.bugi.sensor.sensordata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * C1 수신 계약. 한 물리 노드(deviceCode)의 한 관측 시점(batch) 값 묶음.
 * observedAt/sourceSeq 는 선택이며, measurements 는 채널 code → 값 map 이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BatchIngestRequest {

    @NotBlank
    private String deviceCode;

    // 관측 시각(원본 기준). 없으면 서버 수신 시각으로 대체한다.
    private Instant observedAt;

    // 원본 순서(예: C-MAPSS cycle, CNC 행 번호). 선택.
    private Long sourceSeq;

    @NotEmpty
    private Map<String, Double> measurements;
}

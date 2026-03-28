package dev.yeon.iotsensorplatform.simulator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "시뮬레이터 시작 요청")
public class SimulatorStartRequest {

    @NotNull
    @Schema(description = "장치 ID", example = "1")
    private Long deviceId;

    @NotNull
    @Schema(description = "전송 간격 (초, 최소 1)", example = "2")
    private Integer intervalSeconds;

    @NotNull
    @Schema(description = "전송 횟수 (1~100)", example = "10")
    private Integer count;
}

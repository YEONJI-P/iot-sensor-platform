package dev.yeon.iotsensorplatform.simulator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "시뮬레이터 중단 요청")
public class SimulatorStopRequest {

    @NotNull
    @Schema(description = "장치 ID", example = "1")
    private Long deviceId;
}

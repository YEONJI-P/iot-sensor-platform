package dev.yeon.iotsensorplatform.simulator.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SimulatorStatusResponse {
    private boolean running;
    private int completedCount;
    private int totalCount;
}

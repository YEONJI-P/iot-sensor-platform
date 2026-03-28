package dev.yeon.iotsensorplatform.simulator.controller;

import dev.yeon.iotsensorplatform.device.dto.DeviceResponse;
import dev.yeon.iotsensorplatform.simulator.dto.SimulatorStartRequest;
import dev.yeon.iotsensorplatform.simulator.dto.SimulatorStatusResponse;
import dev.yeon.iotsensorplatform.simulator.dto.SimulatorStopRequest;
import dev.yeon.iotsensorplatform.simulator.service.SimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorService simulatorService;

    // GET /simulator/devices — 내 장치 목록
    @GetMapping("/devices")
    public ResponseEntity<List<DeviceResponse>> getDevices(@AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(simulatorService.getDevices(employeeId));
    }

    // POST /simulator/start
    @PostMapping("/start")
    public ResponseEntity<String> start(
            @AuthenticationPrincipal String employeeId,
            @RequestBody @Valid SimulatorStartRequest request) {
        simulatorService.start(employeeId, request);
        return ResponseEntity.ok("시뮬레이터 시작됐어요");
    }

    // POST /simulator/stop
    @PostMapping("/stop")
    public ResponseEntity<String> stop(
            @AuthenticationPrincipal String employeeId,
            @RequestBody @Valid SimulatorStopRequest request) {
        simulatorService.stop(request.getDeviceId());
        return ResponseEntity.ok("시뮬레이터 중단됐어요");
    }

    // GET /simulator/status/{deviceId}
    @GetMapping("/status/{deviceId}")
    public ResponseEntity<SimulatorStatusResponse> getStatus(
            @AuthenticationPrincipal String employeeId,
            @PathVariable Long deviceId) {
        return ResponseEntity.ok(simulatorService.getStatus(deviceId));
    }
}

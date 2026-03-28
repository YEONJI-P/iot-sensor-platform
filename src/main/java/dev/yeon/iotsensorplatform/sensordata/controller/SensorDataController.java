package dev.yeon.iotsensorplatform.sensordata.controller;

import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataResponse;
import dev.yeon.iotsensorplatform.sensordata.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataService sensorDataService;

    // 외부 장치 또는 수동 입력 — employeeId가 있으면 MANUAL, 없으면 AUTO
    @PostMapping
    public ResponseEntity<String> receive(
            @RequestBody SensorDataRequest request,
            @AuthenticationPrincipal String employeeId) {
        sensorDataService.receive(request, employeeId);
        return ResponseEntity.ok("메시지 발행 완료");
    }

    @GetMapping
    public ResponseEntity<List<SensorDataResponse>> getAllSensorData(
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(sensorDataService.getAllSensorData(employeeId));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<List<SensorDataResponse>> getAllSensorDataByDeviceId(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(sensorDataService.getAllSensorDataByDeviceId(employeeId, deviceId));
    }
}

package dev.bugi.sensor.sensordata.controller;

import dev.bugi.sensor.global.dto.MessageResponse;
import dev.bugi.sensor.sensordata.dto.SensorDataRequest;
import dev.bugi.sensor.sensordata.dto.SensorDataResponse;
import dev.bugi.sensor.sensordata.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataService sensorDataService;

    // 외부 장치 입력
    @PostMapping
    public ResponseEntity<MessageResponse> receive(
            @RequestBody SensorDataRequest request) {
        sensorDataService.receive(request);
        return ResponseEntity.ok(new MessageResponse("센서 데이터 수신 완료"));
    }

    @GetMapping
    public ResponseEntity<Page<SensorDataResponse>> getAllSensorData(
            @AuthenticationPrincipal String employeeId,
            @PageableDefault(size = 50, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(sensorDataService.getAllSensorData(employeeId, pageable));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<List<SensorDataResponse>> getAllSensorDataByDeviceId(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(sensorDataService.getAllSensorDataByDeviceId(employeeId, deviceId));
    }
}

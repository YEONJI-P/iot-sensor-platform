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

    // 센서종단 데이터 > AuthenticationPrincipal 미적용
    // IP 화이트리스트, API Key 등으로 보안 처리
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody SensorDataRequest sensorDataRequest) {
        sensorDataService.receive(sensorDataRequest);
        return ResponseEntity.ok("메시지 발행 완료");
    }

    @GetMapping
    public ResponseEntity<List<SensorDataResponse>> getAllSensorData(@AuthenticationPrincipal String email){
        return ResponseEntity.ok(sensorDataService.getAllSensorData(email));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<List<SensorDataResponse>> getAllSensorDataByDeviceId(@PathVariable Long deviceId,@AuthenticationPrincipal String email){
        return ResponseEntity.ok(sensorDataService.getAllSensorDataByDeviceId(email,deviceId));
    }

}

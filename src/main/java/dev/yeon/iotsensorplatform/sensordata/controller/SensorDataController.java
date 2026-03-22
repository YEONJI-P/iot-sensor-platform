package dev.yeon.iotsensorplatform.sensordata.controller;


import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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


}

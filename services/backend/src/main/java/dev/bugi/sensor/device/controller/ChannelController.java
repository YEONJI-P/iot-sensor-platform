package dev.bugi.sensor.device.controller;

import dev.bugi.sensor.device.dto.ChannelCreateRequest;
import dev.bugi.sensor.device.dto.ChannelResponse;
import dev.bugi.sensor.device.dto.ChannelUpdateRequest;
import dev.bugi.sensor.device.service.ChannelService;
import dev.bugi.sensor.sensordata.dto.ReadingResponse;
import dev.bugi.sensor.sensordata.service.SensorDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C4 채널 API. base path 를 클래스에 두지 않는다 — 등록만 /devices/{deviceId}/channels 아래 있고
 * 조회·수정은 /channels 아래라 메서드별 전체 경로로 매핑한다.
 */
@RestController
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final SensorDataService sensorDataService;

    // 대시보드 드롭다운 소스(접근 범위 필터). deviceId 를 주면 그 장치의 채널만 반환한다.
    @GetMapping("/channels")
    public ResponseEntity<List<ChannelResponse>> getMyChannels(
            @RequestParam(required = false) Long deviceId,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(channelService.getMyChannels(employeeId, deviceId));
    }

    // 채널별 최근 판독(observed_at desc).
    @GetMapping("/channels/{id}/readings")
    public ResponseEntity<List<ReadingResponse>> getReadings(
            @PathVariable Long id,
            @RequestParam(defaultValue = "500") int limit,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(sensorDataService.getReadingsByChannel(employeeId, id, limit));
    }

    // 채널 등록.
    @PostMapping("/devices/{deviceId}/channels")
    public ResponseEntity<ChannelResponse> create(
            @PathVariable Long deviceId,
            @RequestBody @Valid ChannelCreateRequest request,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(channelService.createChannel(deviceId, request, employeeId));
    }

    // 채널 수정(임계값 등).
    @PutMapping("/channels/{id}")
    public ResponseEntity<ChannelResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ChannelUpdateRequest request,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(channelService.updateChannel(id, request, employeeId));
    }
}

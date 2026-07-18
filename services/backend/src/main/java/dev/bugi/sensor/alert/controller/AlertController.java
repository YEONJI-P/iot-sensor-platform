package dev.bugi.sensor.alert.controller;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.dto.DailyAlertCountResponse;
import dev.bugi.sensor.alert.service.AlertService;
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
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // 전체 알림(대시보드) — 접근 가능한 device 범위.
    @GetMapping
    public ResponseEntity<Page<AlertResponse>> getAllAlerts(
            @AuthenticationPrincipal String employeeId,
            @RequestParam(required = false) Long deviceId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(alertService.getAllAlerts(employeeId, deviceId, pageable));
    }

    // 채널별 알림 — 채널 화면.
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<AlertResponse>> getAlertsByChannel(
            @PathVariable Long channelId,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getAlertsByChannel(employeeId, channelId));
    }

    // GET /alerts/recent?channelId={channelId}&limit=20
    @GetMapping("/recent")
    public ResponseEntity<List<AlertResponse>> getRecentAlerts(
            @RequestParam Long channelId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getRecentAlerts(employeeId, channelId, limit));
    }

    // GET /alerts/daily-count?channelId={channelId}&days=7
    @GetMapping("/daily-count")
    public ResponseEntity<List<DailyAlertCountResponse>> getDailyCount(
            @RequestParam Long channelId,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getDailyCount(employeeId, channelId, days));
    }
}

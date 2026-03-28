package dev.yeon.iotsensorplatform.alert.controller;

import dev.yeon.iotsensorplatform.alert.dto.AlertResponse;
import dev.yeon.iotsensorplatform.alert.dto.DailyAlertCountResponse;
import dev.yeon.iotsensorplatform.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAllAlerts(@AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getAllAlerts(employeeId));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<List<AlertResponse>> getAllAlertsByDeviceId(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getAllAlertsByDeviceId(employeeId, deviceId));
    }

    // GET /alerts/recent?deviceId={deviceId}&limit=20
    @GetMapping("/recent")
    public ResponseEntity<List<AlertResponse>> getRecentAlerts(
            @RequestParam Long deviceId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getRecentAlerts(employeeId, deviceId, limit));
    }

    // GET /alerts/daily-count?deviceId={deviceId}&days=7
    @GetMapping("/daily-count")
    public ResponseEntity<List<DailyAlertCountResponse>> getDailyCount(
            @RequestParam Long deviceId,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getDailyCount(employeeId, deviceId, days));
    }
}

package dev.yeon.iotsensorplatform.alert.controller;

import dev.yeon.iotsensorplatform.alert.dto.AlertResponse;
import dev.yeon.iotsensorplatform.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<List<AlertResponse>> getAllAlertsByDeviceId(@PathVariable Long deviceId, @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(alertService.getAllAlertsByDeviceId(employeeId, deviceId));
    }

}

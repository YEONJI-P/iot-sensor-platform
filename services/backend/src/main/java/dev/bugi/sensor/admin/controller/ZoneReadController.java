package dev.bugi.sensor.admin.controller;

import dev.bugi.sensor.admin.dto.ZoneResponse;
import dev.bugi.sensor.admin.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/zones")
@RequiredArgsConstructor
public class ZoneReadController {

    private final ZoneService zoneService;

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> getAccessible(
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(zoneService.getAccessible(employeeId));
    }
}

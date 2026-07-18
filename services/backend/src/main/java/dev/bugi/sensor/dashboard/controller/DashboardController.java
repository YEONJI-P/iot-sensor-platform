package dev.bugi.sensor.dashboard.controller;

import dev.bugi.sensor.dashboard.dto.DashboardOverviewResponse;
import dev.bugi.sensor.dashboard.service.DashboardOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardOverviewService dashboardOverviewService;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewResponse> overview(
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(dashboardOverviewService.getOverview(employeeId));
    }
}

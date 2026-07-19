package dev.bugi.sensor.admin.controller;

import dev.bugi.sensor.admin.dto.FactoryCalendarDtos.Detail;
import dev.bugi.sensor.admin.dto.FactoryCalendarDtos.ReplaceRequest;
import dev.bugi.sensor.admin.dto.FactoryCalendarDtos.Summary;
import dev.bugi.sensor.admin.service.FactoryCalendarAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/factory-calendars")
@RequiredArgsConstructor
public class FactoryCalendarController {

    private final FactoryCalendarAdminService service;

    @GetMapping
    public ResponseEntity<List<Summary>> summaries(Authentication authentication) {
        return ResponseEntity.ok(service.summaries(authentication.getName()));
    }

    @GetMapping("/{factoryId}")
    public ResponseEntity<Detail> get(Authentication authentication, @PathVariable Long factoryId) {
        return ResponseEntity.ok(service.get(authentication.getName(), factoryId));
    }

    @PutMapping("/{factoryId}")
    public ResponseEntity<Detail> replace(Authentication authentication, @PathVariable Long factoryId,
                                          @RequestBody ReplaceRequest request) {
        return ResponseEntity.ok(service.replace(authentication.getName(), factoryId, request));
    }
}

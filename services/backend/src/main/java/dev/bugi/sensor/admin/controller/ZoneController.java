package dev.bugi.sensor.admin.controller;

import dev.bugi.sensor.admin.dto.ZoneRequest;
import dev.bugi.sensor.admin.dto.ZoneResponse;
import dev.bugi.sensor.admin.dto.ZoneUserRequest;
import dev.bugi.sensor.admin.service.ZoneService;
import dev.bugi.sensor.global.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping
    public ResponseEntity<ZoneResponse> create(
            @RequestBody @Valid ZoneRequest request,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(zoneService.create(request, employeeId));
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> getAll(@AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(zoneService.getAll(employeeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ZoneRequest request,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(zoneService.update(id, request, employeeId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal String employeeId) {
        zoneService.delete(id, employeeId);
        return ResponseEntity.ok(new MessageResponse("구역이 삭제됐어요"));
    }

    @PostMapping("/{id}/users")
    public ResponseEntity<MessageResponse> addUser(
            @PathVariable Long id,
            @RequestBody @Valid ZoneUserRequest request,
            @AuthenticationPrincipal String employeeId) {
        zoneService.addUser(id, request.getUserId(), employeeId);
        return ResponseEntity.ok(new MessageResponse("구역에 사용자가 추가됐어요"));
    }

    @DeleteMapping("/{id}/users/{userId}")
    public ResponseEntity<MessageResponse> removeUser(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal String employeeId) {
        zoneService.removeUser(id, userId, employeeId);
        return ResponseEntity.ok(new MessageResponse("구역에서 사용자가 제거됐어요"));
    }
}

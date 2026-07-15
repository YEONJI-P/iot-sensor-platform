package dev.bugi.sensor.device.controller;

import dev.bugi.sensor.device.dto.DeviceRegisterRequest;
import dev.bugi.sensor.device.dto.DeviceResponse;
import dev.bugi.sensor.device.dto.DeviceUpdateRequest;
import dev.bugi.sensor.device.service.DeviceService;
import dev.bugi.sensor.global.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;
    // 조회
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getMyDevices(@AuthenticationPrincipal String employeeId){
        return ResponseEntity.ok(deviceService.getMyDevices(employeeId));
    }
    // 등록
    @PostMapping
    public ResponseEntity<DeviceResponse> register(@RequestBody @Valid DeviceRegisterRequest request, @AuthenticationPrincipal String employeeId){
        return ResponseEntity.ok(deviceService.register(request, employeeId));
    }
    // 수정
    @PutMapping("/{id}")
    public ResponseEntity<DeviceResponse> update(@PathVariable Long id, @RequestBody @Valid DeviceUpdateRequest request, @AuthenticationPrincipal String employeeId){
        return ResponseEntity.ok(deviceService.update(id, request, employeeId));
    }

    //삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id, @AuthenticationPrincipal String employeeId){
        deviceService.delete(id, employeeId);
        return ResponseEntity.ok(new MessageResponse("장치 정보가 제거되었어요."));
    }

}

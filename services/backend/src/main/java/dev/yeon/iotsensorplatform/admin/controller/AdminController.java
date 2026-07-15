package dev.yeon.iotsensorplatform.admin.controller;

import dev.yeon.iotsensorplatform.admin.dto.UserResponse;
import dev.yeon.iotsensorplatform.admin.service.AdminService;
import dev.yeon.iotsensorplatform.global.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // GET /admin/users/pending — 가입 대기자 목록 (FACTORY_ADMIN은 소속 공장만)
    @GetMapping("/users/pending")
    public ResponseEntity<List<UserResponse>> getPendingUsers(@AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(adminService.getPendingUsers(employeeId));
    }

    // GET /admin/users — 사용자 목록 (FACTORY_ADMIN은 소속 공장만)
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers(@AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(adminService.getAllUsers(employeeId));
    }

    // PATCH /admin/users/{id}/approve — 승인
    @PatchMapping("/users/{id}/approve")
    public ResponseEntity<MessageResponse> approveUser(@PathVariable Long id,
                                                       @AuthenticationPrincipal String employeeId) {
        adminService.approveUser(id, employeeId);
        return ResponseEntity.ok(new MessageResponse("승인됐어요"));
    }

    // PATCH /admin/users/{id}/reject — 반려
    @PatchMapping("/users/{id}/reject")
    public ResponseEntity<MessageResponse> rejectUser(@PathVariable Long id,
                                                      @AuthenticationPrincipal String employeeId) {
        adminService.rejectUser(id, employeeId);
        return ResponseEntity.ok(new MessageResponse("반려됐어요"));
    }
}

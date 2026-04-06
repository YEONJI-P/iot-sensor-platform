package dev.yeon.iotsensorplatform.admin.controller;

import dev.yeon.iotsensorplatform.admin.dto.UserResponse;
import dev.yeon.iotsensorplatform.admin.service.AdminService;
import dev.yeon.iotsensorplatform.global.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // GET /admin/users/pending — 가입 대기자 목록
    @GetMapping("/users/pending")
    public ResponseEntity<List<UserResponse>> getPendingUsers() {
        return ResponseEntity.ok(adminService.getPendingUsers());
    }

    // GET /admin/users — 전체 사용자 목록
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // PATCH /admin/users/{id}/approve — 승인
    @PatchMapping("/users/{id}/approve")
    public ResponseEntity<MessageResponse> approveUser(@PathVariable Long id) {
        adminService.approveUser(id);
        return ResponseEntity.ok(new MessageResponse("승인됐어요"));
    }

    // PATCH /admin/users/{id}/reject — 반려
    @PatchMapping("/users/{id}/reject")
    public ResponseEntity<MessageResponse> rejectUser(@PathVariable Long id) {
        adminService.rejectUser(id);
        return ResponseEntity.ok(new MessageResponse("반려됐어요"));
    }
}

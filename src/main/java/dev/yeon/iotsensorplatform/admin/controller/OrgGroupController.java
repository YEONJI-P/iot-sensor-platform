package dev.yeon.iotsensorplatform.admin.controller;

import dev.yeon.iotsensorplatform.admin.dto.GroupRequest;
import dev.yeon.iotsensorplatform.admin.dto.GroupResponse;
import dev.yeon.iotsensorplatform.admin.dto.GroupUserRequest;
import dev.yeon.iotsensorplatform.admin.service.OrgGroupService;
import dev.yeon.iotsensorplatform.global.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/groups")
@RequiredArgsConstructor
public class OrgGroupController {

    private final OrgGroupService orgGroupService;

    @PostMapping
    public ResponseEntity<GroupResponse> create(
            @RequestBody @Valid GroupRequest request,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(orgGroupService.create(request, employeeId));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getAll(@AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(orgGroupService.getAll(employeeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid GroupRequest request,
            @AuthenticationPrincipal String employeeId) {
        return ResponseEntity.ok(orgGroupService.update(id, request, employeeId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal String employeeId) {
        orgGroupService.delete(id, employeeId);
        return ResponseEntity.ok(new MessageResponse("그룹이 삭제됐어요"));
    }

    @PostMapping("/{id}/users")
    public ResponseEntity<MessageResponse> addUser(
            @PathVariable Long id,
            @RequestBody @Valid GroupUserRequest request,
            @AuthenticationPrincipal String employeeId) {
        orgGroupService.addUser(id, request.getUserId(), employeeId);
        return ResponseEntity.ok(new MessageResponse("그룹에 사용자가 추가됐어요"));
    }

    @DeleteMapping("/{id}/users/{userId}")
    public ResponseEntity<MessageResponse> removeUser(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal String employeeId) {
        orgGroupService.removeUser(id, userId, employeeId);
        return ResponseEntity.ok(new MessageResponse("그룹에서 사용자가 제거됐어요"));
    }
}

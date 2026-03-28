package dev.yeon.iotsensorplatform.auth.controller;

import dev.yeon.iotsensorplatform.auth.dto.LoginRequest;
import dev.yeon.iotsensorplatform.auth.dto.RegisterRequest;
import dev.yeon.iotsensorplatform.auth.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /auth/register — 가입 신청 (PENDING 상태로 저장)
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("가입 신청이 완료됐어요. 관리자 승인 후 로그인 가능해요");
    }

    // POST /auth/login — 사원번호 기반 로그인 (ACTIVE만 허용)
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody @Valid LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(token);
    }
}

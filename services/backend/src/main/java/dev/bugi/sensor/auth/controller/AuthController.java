package dev.bugi.sensor.auth.controller;

import dev.bugi.sensor.auth.dto.LoginRequest;
import dev.bugi.sensor.auth.dto.FactoryOptionResponse;
import dev.bugi.sensor.auth.dto.RefreshRequest;
import dev.bugi.sensor.auth.dto.RegisterRequest;
import dev.bugi.sensor.auth.dto.TokenResponse;
import dev.bugi.sensor.auth.service.AuthService;
import dev.bugi.sensor.global.dto.MessageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @SecurityRequirements
    @GetMapping("/factories")
    public ResponseEntity<List<FactoryOptionResponse>> getFactories() {
        return ResponseEntity.ok(authService.getFactories());
    }

    // POST /auth/register — 가입 신청 (PENDING 상태로 저장)
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(new MessageResponse("가입 신청이 완료됐어요. 관리자 승인 후 로그인 가능해요"));
    }

    // POST /auth/login — 사원번호 기반 로그인 (ACTIVE만 허용)
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid RefreshRequest reqeust){
        return ResponseEntity.ok(authService.refresh(reqeust.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal String employeeId){
        authService.logout(employeeId);
        return ResponseEntity.ok(new MessageResponse("로그아웃 되었어요"));
    }

}

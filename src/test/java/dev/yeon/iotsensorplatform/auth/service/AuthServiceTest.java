package dev.yeon.iotsensorplatform.auth.service;

import dev.yeon.iotsensorplatform.auth.dto.LoginRequest;
import dev.yeon.iotsensorplatform.auth.dto.RegisterRequest;
import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private AuthService authService;

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, null, null);
        when(userRepository.existsByEmployeeId("EMP001")).thenReturn(false);
        when(passwordEncoder.encode("password123!")).thenReturn("encoded_password");

        authService.register(request);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_employeeId_duplicate() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, null, null);
        when(userRepository.existsByEmployeeId("EMP001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    void login_success() {
        User mockUser = User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.ACTIVE)
                .build();
        LoginRequest request = new LoginRequest("EMP001", "password123!");

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123!", "encoded_password")).thenReturn(true);
        when(jwtUtil.createToken("EMP001", "VIEWER")).thenReturn("mock_token");

        String token = authService.login(request);

        assertThat(token).isEqualTo("mock_token");
        verify(jwtUtil, times(1)).createToken("EMP001", "VIEWER");
    }

    @Test
    void login_fail_employeeId_not_found() {
        LoginRequest request = new LoginRequest("NOTEXIST", "password123!");
        when(userRepository.findByEmployeeId("NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
    }

    @Test
    void login_fail_wrong_password() {
        User mockUser = User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.ACTIVE)
                .build();
        LoginRequest request = new LoginRequest("EMP001", "wrongpassword");

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongpassword", "encoded_password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
    }

    @Test
    void login_fail_pending_status() {
        User mockUser = User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.PENDING)
                .build();
        LoginRequest request = new LoginRequest("EMP001", "password123!");

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123!", "encoded_password")).thenReturn(true);

        assertThrows(DisabledException.class, () -> authService.login(request));
    }

    @Test
    void login_fail_rejected_status() {
        User mockUser = User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.REJECTED)
                .build();
        LoginRequest request = new LoginRequest("EMP001", "password123!");

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123!", "encoded_password")).thenReturn(true);

        assertThrows(DisabledException.class, () -> authService.login(request));
    }
}

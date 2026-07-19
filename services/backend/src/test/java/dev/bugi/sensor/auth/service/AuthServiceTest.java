package dev.bugi.sensor.auth.service;

import dev.bugi.sensor.auth.dto.LoginRequest;
import dev.bugi.sensor.auth.dto.FactoryOptionResponse;
import dev.bugi.sensor.auth.dto.RegisterRequest;
import dev.bugi.sensor.auth.dto.TokenResponse;
import dev.bugi.sensor.auth.util.JwtUtil;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.repository.FactoryRepository;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import dev.bugi.sensor.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FactoryRepository factoryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RefreshTokenService refreshTokenService;
    @InjectMocks
    private AuthService authService;

    @Test
    void register는_유효한_공장을_지정한_pending_viewer를_저장한다() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, 1L);
        Factory factory = mock(Factory.class);
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory));
        when(passwordEncoder.encode("password123!")).thenReturn("encoded_password");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getFactory()).isSameAs(factory);
        assertThat(saved.getRole()).isEqualTo(Role.VIEWER);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void register는_공장_id가_없으면_거부한다() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertThat(exception).hasMessage("가입할 공장은 필수예요");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(factoryRepository);
    }

    @Test
    void register는_0_이하_공장_id를_거부한다() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, 0L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertThat(exception).hasMessage("공장 ID는 1 이상이어야 해요");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(factoryRepository);
    }

    @Test
    void register는_존재하지_않는_공장_id를_거부한다() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, 99L);
        when(factoryRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertThat(exception).hasMessage("존재하지 않는 공장이에요 - id 99");
        verify(userRepository, never()).save(any());
    }

    @Test
    void public_공장_목록은_이름순으로_id와_name만_반환한다() {
        Factory second = mock(Factory.class);
        when(second.getId()).thenReturn(2L);
        when(second.getName()).thenReturn("나 공장");
        Factory first = mock(Factory.class);
        when(first.getId()).thenReturn(1L);
        when(first.getName()).thenReturn("가 공장");
        when(factoryRepository.findAll()).thenReturn(List.of(second, first));

        List<FactoryOptionResponse> result = authService.getFactories();

        assertThat(result).containsExactly(
                new FactoryOptionResponse(1L, "가 공장"),
                new FactoryOptionResponse(2L, "나 공장")
        );
    }

    @Test
    void register_employeeId_duplicate() {
        RegisterRequest request = new RegisterRequest("EMP001", "홍길동", "password123!", null, 1L);
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
        when(jwtUtil.createRefreshToken("EMP001")).thenReturn("mock_refresh_token");

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("mock_token");
        assertThat(response.refreshToken()).isEqualTo("mock_refresh_token");
        verify(refreshTokenService,times(1)).save("EMP001","mock_refresh_token");
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

    @Test
    void refresh_success(){
        User mockUser = User.builder().employeeId("EMP001")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.ACTIVE).build();

        when(jwtUtil.validateToken("old_refresh")).thenReturn(true);
        when(jwtUtil.getEmployeeId("old_refresh")).thenReturn("EMP001");
        when(refreshTokenService.validate("EMP001","old_refresh")).thenReturn(true);
        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(mockUser));
        when(jwtUtil.createToken("EMP001","VIEWER")).thenReturn("new_access_token");
        when(jwtUtil.createRefreshToken("EMP001")).thenReturn("new_refresh_token");

        TokenResponse response = authService.refresh("old_refresh");

        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.refreshToken()).isEqualTo("new_refresh_token");
        verify(refreshTokenService,times(1)).save("EMP001","new_refresh_token");

    }

    @Test
    void refresh_fail_invalid_token(){
        when(jwtUtil.validateToken("bad_token")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->authService.refresh("bad_token"));
    }

    @Test
    void refresh_fail_token_mismatch(){
        when(jwtUtil.validateToken("stolen_token")).thenReturn(true);
        when(jwtUtil.getEmployeeId("stolen_token")).thenReturn("EMP001");
        when(refreshTokenService.validate("EMP001","stolen_token")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,()->authService.refresh("stolen_token"));
        // 불일치 시 저장 토큰 삭제(강제 로그아웃)까지 수행하는지 검증
        verify(refreshTokenService,times(1)).delete("EMP001");
    }

    @Test
    void logout_success(){
        authService.logout("EMP001");
        verify(refreshTokenService,times(1)).delete("EMP001");
    }
}

package dev.yeon.iotsensorplatform.auth.service;

import dev.yeon.iotsensorplatform.auth.dto.LoginRequest;
import dev.yeon.iotsensorplatform.auth.dto.SignupRequest;
import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @Mock        → 가짜 객체 생성
    // @InjectMocks → 진짜 객체 생성 + @Mock들 자동 주입
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private AuthService authService;

    @Test
    void signup_success() {
        // given
        // 어떤 email, password 가 주어짐
        SignupRequest signupRequest = new SignupRequest("test@test.com","password123");
        // signup 이 호출되면
        // 중복확인도 ok, password Encode 값도 들어오는 걸로 가정
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");
        authService.signup(signupRequest);
        // repo 저장 함수가 호출됐는지 verify
        verify(userRepository,times(1)).save(any(User.class));
    }

    @Test
    void signup_email_duplicate(){
        // existsByEmail이 true일때 오류가 정상적으로 발생하는지 체크
        SignupRequest signupRequest = new SignupRequest("test@test.com","password123");
        // existsByEmail이 true라고 가정
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);
        // authService의 signup 함수가 IllegalArgumentException을 제대로 뱉는지 확인
        assertThrows(IllegalArgumentException.class, ()->{
            authService.signup(signupRequest);
        });
    }

    @Test
    void login_success() {

        // === given
        // login request
        User mockUser = User.builder()
                .email("test@test.com")
                .password("encoded_password")
                .role(User.Role.USER)
                .build();
        LoginRequest request = new LoginRequest("test@test.com","password123");
        // user Repo 에서 email로 user 찾으면 mockUser 찾음
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(mockUser));
        // 암호화된 password도 match 하면 조회된 mockUser 와 동일함
        when(passwordEncoder.matches(request.getPassword(),mockUser.getPassword())).thenReturn(true);
        // jwtUtil 은 해당 email을 주면 token "mock_token" 발급함
        when(jwtUtil.createToken(request.getEmail())).thenReturn("mock_token");

        // === when
        // 이제 login 실행
        // authService 로그인 하면 token 발급됨
        String token = authService.login(request);

        // === then
        // 두가지 검사
        // 발급된 토큰이 "mock_token"인지 검사
        assertThat(token).isEqualTo("mock_token");
        // jwtUtil 에서 createToken 이 호출됐는지 검사
        verify(jwtUtil,times(1)).createToken(request.getEmail());
    }


    @Test
    void login_fail_email(){
        // login request
        // email 오류라고 가정
        // 이 때 오류가 잘 발생하는지
        // === given
        LoginRequest request = new LoginRequest("test@test.com","password123");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        // === when
        // === then
        assertThrows(IllegalArgumentException.class, ()->{
            authService.login(request);
        });
    }

    @Test
    void login_fail_password(){

        // === given
        User mockUser = User.builder()
                .email("test@test.com")
                .password("encoded_password")
                .role(User.Role.USER)
                .build();
        LoginRequest request = new LoginRequest("test@test.com","password123");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(request.getPassword(),mockUser.getPassword())).thenReturn(false);
        // === when
        // === then
        assertThrows(IllegalArgumentException.class, ()->{
            authService.login(request);
        });
    }
}
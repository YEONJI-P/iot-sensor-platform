package dev.bugi.sensor.auth.service;

import dev.bugi.sensor.auth.dto.LoginRequest;
import dev.bugi.sensor.auth.dto.RegisterRequest;
import dev.bugi.sensor.auth.dto.TokenResponse;
import dev.bugi.sensor.auth.util.JwtUtil;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.repository.FactoryRepository;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final FactoryRepository factoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new IllegalArgumentException("이미 사용 중인 사원번호예요");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일이에요");
        }

        Factory org = null;
        if (request.getFactoryId() != null) {
            org = factoryRepository.findById(request.getFactoryId()).orElse(null);
        }

        User user = User.builder()
                .employeeId(request.getEmployeeId())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .factory(org)
                .role(Role.VIEWER)
                .status(UserStatus.PENDING)
                .build();
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmployeeId(request.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 틀렸어요");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new DisabledException("승인 대기 중인 계정이에요. 관리자 승인 후 로그인 가능해요");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new DisabledException("반려된 계정이에요. 관리자에게 문의해주세요");
        }
        String accessToken = jwtUtil.createToken(user.getEmployeeId(),user.getRole().name());
        String refreshToken = jwtUtil.createRefreshToken(user.getEmployeeId());
        refreshTokenService.save(user.getEmployeeId(),refreshToken);

        return new TokenResponse(accessToken,refreshToken);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken){
        if(!jwtUtil.validateToken(refreshToken)){
            throw new IllegalArgumentException("유효하지 않은 로그인 정보에요");
        }

        /*
        탈취 이슈 대응 (refresh token rotation)
          정상 사용자 A 로그인 → refresh_tokens[EMP001] = "tokenB"(회전됨)
          이후 A가 구 토큰 tokenA로 refresh → 저장 값과 불일치
          → 저장된 토큰을 즉시 삭제해 양쪽(정상/공격자) 모두 강제 로그아웃시키고 재로그인 유도
         */

        String employeeId = jwtUtil.getEmployeeId(refreshToken);
        if(!refreshTokenService.validate(employeeId,refreshToken)){
            refreshTokenService.delete(employeeId);
            log.warn("refresh token 불일치 - 저장 토큰 삭제(강제 로그아웃) - employeeId: {}", employeeId);
            throw new IllegalArgumentException("로그인 정보가 일치하지 않아요");
        }

        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(()->new IllegalArgumentException("존재하지 않는 사용자예요"));
        if (user.getStatus() !=UserStatus.ACTIVE){
            throw new DisabledException("사용할 수 없는 계정이에요");
        }

        String newAccessToken = jwtUtil.createToken(employeeId,user.getRole().name());
        String newRefreshToken = jwtUtil.createRefreshToken(employeeId);
        refreshTokenService.save(employeeId,newRefreshToken);

        return new TokenResponse(newAccessToken,newRefreshToken);
    }

    public void logout(String employeeId){
        refreshTokenService.delete(employeeId);
    }
}

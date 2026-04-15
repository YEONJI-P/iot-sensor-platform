package dev.yeon.iotsensorplatform.auth.service;

import dev.yeon.iotsensorplatform.auth.dto.LoginRequest;
import dev.yeon.iotsensorplatform.auth.dto.RegisterRequest;
import dev.yeon.iotsensorplatform.auth.dto.TokenResponse;
import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
import dev.yeon.iotsensorplatform.organization.entity.Organization;
import dev.yeon.iotsensorplatform.organization.repository.OrganizationRepository;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
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

        Organization org = null;
        if (request.getOrganizationId() != null) {
            org = organizationRepository.findById(request.getOrganizationId()).orElse(null);
        }

        User user = User.builder()
                .employeeId(request.getEmployeeId())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .department(request.getDepartment())
                .organization(org)
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
        탈취 이슈 발생 시나리오
          1. 정상 사용자 A가 로그인 → Redis: refresh:EMP001 = "tokenA"
          2. 공격자가 tokenA 탈취
          3. 공격자가 /auth/refresh 호출 → 새 토큰 발급 + Redis: refresh:EMP001 = "tokenB"
          4. 이후 사용자 A가 /auth/refresh 호출 (tokenA로)
             → JWT 파싱은 통과 (아직 만료 안 됨)
             → Redis 비교: "tokenA" ≠ "tokenB" → 불일치

        - 해당 employeeId의 Redis 토큰 즉시 삭제 (양쪽 모두 강제 로그아웃)
        - 보안 알림 발송
        - 로그 기록
         */

        String employeeId = jwtUtil.getEmployeeId(refreshToken);
        if(!refreshTokenService.validate(employeeId,refreshToken)){
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

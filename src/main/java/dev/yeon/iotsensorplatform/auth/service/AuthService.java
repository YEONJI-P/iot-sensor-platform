package dev.yeon.iotsensorplatform.auth.service;

import dev.yeon.iotsensorplatform.auth.dto.LoginRequest;
import dev.yeon.iotsensorplatform.auth.dto.RegisterRequest;
import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new IllegalArgumentException("이미 사용 중인 사원번호예요");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일이에요");
        }
        User user = request.toEntity(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest request) {
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

        return jwtUtil.createToken(user.getEmployeeId(), user.getRole().name());
    }
}

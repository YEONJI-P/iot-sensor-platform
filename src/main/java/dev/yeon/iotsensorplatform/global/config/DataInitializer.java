package dev.yeon.iotsensorplatform.global.config;

import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_PW = "admin1234!";
    private static final String TEST_PW  = "test1234!";

    @Override
    public void run(ApplicationArguments args) {
        List<AccountSeed> seeds = List.of(
            new AccountSeed("ADMIN001", "시스템관리자",  null,                        Role.SUPER_ADMIN,    ADMIN_PW),
            new AccountSeed("UA001",    "사용자관리자",  "user-admin@test.com",       Role.USER_ADMIN,     ADMIN_PW),
            new AccountSeed("DM001",    "장치관리자",    "device-manager@test.com",   Role.DEVICE_MANAGER, TEST_PW),
            new AccountSeed("DI001",    "데이터입력자",  "data-inputter@test.com",    Role.DATA_INPUTTER,  TEST_PW),
            new AccountSeed("DA001",    "데이터분석자",  "data-analyst@test.com",     Role.DATA_ANALYST,   TEST_PW),
            new AccountSeed("VW001",    "뷰어",          "viewer@test.com",           Role.VIEWER,         TEST_PW)
        );

        for (AccountSeed seed : seeds) {
            if (!userRepository.existsByEmployeeId(seed.employeeId())) {
                userRepository.save(User.builder()
                        .employeeId(seed.employeeId())
                        .name(seed.name())
                        .email(seed.email())
                        .password(passwordEncoder.encode(seed.password()))
                        .role(seed.role())
                        .status(UserStatus.ACTIVE)
                        .organizationId(1L)
                        .build());
                log.info("기본 계정 생성: {} ({})", seed.employeeId(), seed.role());
            }
        }
    }

    private record AccountSeed(String employeeId, String name, String email, Role role, String password) {}
}

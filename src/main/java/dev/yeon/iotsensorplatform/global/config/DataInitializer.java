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

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByRole(Role.SUPER_ADMIN)) {
            User superAdmin = User.builder()
                    .employeeId("ADMIN001")
                    .name("시스템관리자")
                    .password(passwordEncoder.encode("admin1234!"))
                    .role(Role.SUPER_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .organizationId(1L)
                    .build();
            userRepository.save(superAdmin);
            log.info("SUPER_ADMIN 초기 계정이 생성됐어요 (employeeId: ADMIN001)");
        }
    }
}

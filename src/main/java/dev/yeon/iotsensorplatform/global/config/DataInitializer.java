package dev.yeon.iotsensorplatform.global.config;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.organization.entity.GroupUser;
import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import dev.yeon.iotsensorplatform.organization.entity.Organization;
import dev.yeon.iotsensorplatform.organization.repository.GroupUserRepository;
import dev.yeon.iotsensorplatform.organization.repository.OrgGroupRepository;
import dev.yeon.iotsensorplatform.organization.repository.OrganizationRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final OrganizationRepository organizationRepository;
    private final OrgGroupRepository orgGroupRepository;
    private final GroupUserRepository groupUserRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 이미 초기화된 경우 스킵
        if (userRepository.existsByEmployeeId("ADMIN001")) {
            log.info("초기 데이터가 이미 존재합니다. 스킵합니다.");
            return;
        }

        // ── Organization ──────────────────────────────────────
        Organization org = organizationRepository.save(
                Organization.builder()
                        .name("스마트공장A")
                        .description("스마트 제조 공장 A동")
                        .build()
        );
        log.info("조직 생성: {}", org.getName());

        // ── Groups ────────────────────────────────────────────
        OrgGroup group1 = orgGroupRepository.save(
                OrgGroup.builder()
                        .organization(org)
                        .name("1구역(생산라인)")
                        .description("1구역 생산 라인 담당 그룹")
                        .build()
        );
        OrgGroup group2 = orgGroupRepository.save(
                OrgGroup.builder()
                        .organization(org)
                        .name("2구역(품질검사)")
                        .description("2구역 품질 검사 담당 그룹")
                        .build()
        );
        log.info("그룹 생성: {}, {}", group1.getName(), group2.getName());

        // ── Users ─────────────────────────────────────────────
        User admin = createUser("ADMIN001", "시스템관리자", null, Role.SUPER_ADMIN, "admin1234!", org);
        User manager = createUser("MGR001", "현장관리자", "mgr@test.com", Role.USER_ADMIN, "mgr1234!", org);
        User dev = createUser("DEV001", "장치담당자", "dev@test.com", Role.DEVICE_MANAGER, "dev1234!", org);
        User inputter = createUser("INP001", "데이터입력자", "inp@test.com", Role.DATA_INPUTTER, "inp1234!", org);
        User analyst = createUser("ANL001", "분석담당자", "anl@test.com", Role.DATA_ANALYST, "anl1234!", org);
        User viewer = createUser("VWR001", "열람자", "vwr@test.com", Role.VIEWER, "vwr1234!", org);
        User inputter2 = createUser("INP002", "데이터입력자2", "inp2@test.com", Role.DATA_INPUTTER, "inp2234!", org);

        // ── GroupUser 매핑 — Group1 ───────────────────────────
        addToGroup(group1, dev);
        addToGroup(group1, inputter);
        addToGroup(group1, analyst);
        addToGroup(group1, viewer);

        // ── GroupUser 매핑 — Group2 ───────────────────────────
        addToGroup(group2, inputter2);

        log.info("사용자 및 그룹 매핑 완료 (총 {}명)", 7);

        // ── Devices — Group1 ──────────────────────────────────
        deviceRepository.save(Device.builder()
                .group(group1).name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .thresholdValue(80.0).location("1구역A").build());
        deviceRepository.save(Device.builder()
                .group(group1).name("진동센서1")
                .type(Device.DeviceType.VIBRATION)
                .thresholdValue(50.0).location("1구역B").build());

        // ── Devices — Group2 ──────────────────────────────────
        deviceRepository.save(Device.builder()
                .group(group2).name("압력센서1")
                .type(Device.DeviceType.PRESSURE)
                .thresholdValue(60.0).location("2구역A").build());

        log.info("장치 생성 완료 (총 3개)");
        log.info("DataInitializer 완료 — 조직: {}", org.getName());
        // Suppress unused variable warnings
        log.debug("매핑 확인 — manager: {}", manager.getEmployeeId());
    }

    private User createUser(String employeeId, String name, String email,
                             Role role, String password, Organization org) {
        User user = userRepository.save(User.builder()
                .employeeId(employeeId)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .status(UserStatus.ACTIVE)
                .organization(org)
                .build());
        log.info("계정 생성: {} ({})", employeeId, role);
        return user;
    }

    private void addToGroup(OrgGroup group, User user) {
        groupUserRepository.save(GroupUser.builder().group(group).user(user).build());
    }
}

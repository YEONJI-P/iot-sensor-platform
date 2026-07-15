package dev.yeon.iotsensorplatform.global.service;

import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.organization.repository.GroupUserRepository;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock GroupUserRepository groupUserRepository;

    @InjectMocks
    AccessControlService accessControlService;

    private User userWithRole(Role role) {
        return User.builder()
                .employeeId("EMP001")
                .name("사용자")
                .password("encoded_password")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void assertCanMutateDevice_viewer_forbidden() {
        User viewer = userWithRole(Role.VIEWER);
        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanMutateDevice(viewer));
    }

    @Test
    void assertCanMutateDevice_member_allowed() {
        User member = userWithRole(Role.MEMBER);
        assertDoesNotThrow(() -> accessControlService.assertCanMutateDevice(member));
    }

    @Test
    void assertCanMutateDevice_system_admin_allowed() {
        User admin = userWithRole(Role.SYSTEM_ADMIN);
        assertDoesNotThrow(() -> accessControlService.assertCanMutateDevice(admin));
    }
}

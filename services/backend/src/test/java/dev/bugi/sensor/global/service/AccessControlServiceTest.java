package dev.bugi.sensor.global.service;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.ZoneUser;
import dev.bugi.sensor.factory.repository.ZoneUserRepository;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock ZoneUserRepository zoneUserRepository;

    @InjectMocks
    AccessControlService accessControlService;

    // 엔티티 id 는 생성 자동값(세터 없음)이라 목으로 통제한다.
    private User user(Role role, Long userId, Long factoryId) {
        User user = mock(User.class);
        lenient().when(user.getRole()).thenReturn(role);
        lenient().when(user.getId()).thenReturn(userId);
        if (factoryId != null) {
            Factory factory = mock(Factory.class);
            lenient().when(factory.getId()).thenReturn(factoryId);
            lenient().when(user.getFactory()).thenReturn(factory);
        }
        return user;
    }

    private Device deviceInZone(Long zoneId, Long factoryId) {
        Factory factory = mock(Factory.class);
        lenient().when(factory.getId()).thenReturn(factoryId);
        Zone zone = mock(Zone.class);
        lenient().when(zone.getId()).thenReturn(zoneId);
        lenient().when(zone.getFactory()).thenReturn(factory);
        Device device = mock(Device.class);
        lenient().when(device.getZone()).thenReturn(zone);
        return device;
    }

    private ZoneUser membershipOfZone(Long zoneId) {
        Zone zone = mock(Zone.class);
        when(zone.getId()).thenReturn(zoneId);
        ZoneUser zu = mock(ZoneUser.class);
        when(zu.getZone()).thenReturn(zone);
        return zu;
    }

    // ── getAccessibleDeviceIds : 4 role 분기 + factory null ────────────

    @Test
    void accessibleDeviceIds_system_admin_returns_all() {
        User admin = user(Role.SYSTEM_ADMIN, 1L, null);
        when(deviceRepository.findAllIds()).thenReturn(List.of(1L, 2L, 3L));

        assertThat(accessControlService.getAccessibleDeviceIds(admin)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void accessibleDeviceIds_factory_admin_scoped_to_factory() {
        User admin = user(Role.FACTORY_ADMIN, 1L, 10L);
        when(deviceRepository.findIdsByFactoryId(10L)).thenReturn(List.of(5L, 6L));

        assertThat(accessControlService.getAccessibleDeviceIds(admin)).containsExactly(5L, 6L);
    }

    @Test
    void accessibleDeviceIds_factory_admin_without_factory_is_empty() {
        User admin = user(Role.FACTORY_ADMIN, 1L, null);

        assertThat(accessControlService.getAccessibleDeviceIds(admin)).isEmpty();
        verify(deviceRepository, never()).findIdsByFactoryId(any());
    }

    @Test
    void accessibleDeviceIds_member_scoped_to_zones() {
        User member = user(Role.MEMBER, 2L, null);
        ZoneUser membership = membershipOfZone(100L);
        when(zoneUserRepository.findAllByUserId(2L)).thenReturn(List.of(membership));
        when(deviceRepository.findIdsByZoneIdIn(List.of(100L))).thenReturn(List.of(7L));

        assertThat(accessControlService.getAccessibleDeviceIds(member)).containsExactly(7L);
    }

    @Test
    void accessibleDeviceIds_viewer_with_no_zone_is_empty() {
        User viewer = user(Role.VIEWER, 3L, null);
        when(zoneUserRepository.findAllByUserId(3L)).thenReturn(List.of());

        assertThat(accessControlService.getAccessibleDeviceIds(viewer)).isEmpty();
        verify(deviceRepository, never()).findIdsByZoneIdIn(any());
    }

    // ── assertCanAccessDevice : zone null · 타 공장 차단 ───────────────

    @Test
    void assertCanAccessDevice_system_admin_always_allowed() {
        User admin = user(Role.SYSTEM_ADMIN, 1L, null);
        Device device = deviceInZone(100L, 10L);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessDevice(admin, device));
    }

    @Test
    void assertCanAccessDevice_factory_admin_same_factory_allowed() {
        User admin = user(Role.FACTORY_ADMIN, 1L, 10L);
        Device device = deviceInZone(100L, 10L);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessDevice(admin, device));
    }

    @Test
    void assertCanAccessDevice_factory_admin_other_factory_forbidden() {
        User admin = user(Role.FACTORY_ADMIN, 1L, 10L);
        Device device = deviceInZone(100L, 99L); // 다른 공장

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanAccessDevice(admin, device));
    }

    @Test
    void assertCanAccessDevice_factory_admin_zone_null_forbidden() {
        User admin = user(Role.FACTORY_ADMIN, 1L, 10L);
        Device device = mock(Device.class);
        when(device.getZone()).thenReturn(null);

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanAccessDevice(admin, device));
    }

    @Test
    void assertCanAccessDevice_member_in_zone_allowed() {
        User member = user(Role.MEMBER, 2L, null);
        ZoneUser membership = membershipOfZone(100L);
        when(zoneUserRepository.findAllByUserId(2L)).thenReturn(List.of(membership));
        Device device = deviceInZone(100L, 10L);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessDevice(member, device));
    }

    @Test
    void assertCanAccessDevice_member_other_zone_forbidden() {
        User member = user(Role.MEMBER, 2L, null);
        ZoneUser membership = membershipOfZone(100L);
        when(zoneUserRepository.findAllByUserId(2L)).thenReturn(List.of(membership));
        Device device = deviceInZone(200L, 10L); // 소속 아닌 구역

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanAccessDevice(member, device));
    }

    @Test
    void assertCanAccessDevice_member_zone_null_forbidden() {
        User member = user(Role.MEMBER, 2L, null);
        ZoneUser membership = membershipOfZone(100L);
        when(zoneUserRepository.findAllByUserId(2L)).thenReturn(List.of(membership));
        Device device = mock(Device.class);
        when(device.getZone()).thenReturn(null);

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanAccessDevice(member, device));
    }

    // ── assertCanMutateDevice : VIEWER 만 차단 ─────────────────────────

    @Test
    void assertCanMutateDevice_viewer_forbidden() {
        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanMutateDevice(user(Role.VIEWER, 3L, null)));
    }

    @Test
    void assertCanMutateDevice_member_allowed() {
        assertDoesNotThrow(() -> accessControlService.assertCanMutateDevice(user(Role.MEMBER, 2L, null)));
    }

    @Test
    void assertCanMutateDevice_factory_admin_allowed() {
        assertDoesNotThrow(() -> accessControlService.assertCanMutateDevice(user(Role.FACTORY_ADMIN, 1L, 10L)));
    }

    @Test
    void assertCanMutateDevice_system_admin_allowed() {
        assertDoesNotThrow(() -> accessControlService.assertCanMutateDevice(user(Role.SYSTEM_ADMIN, 1L, null)));
    }

    // ── assertCanManageZone : MEMBER 구역 소속 검사 · 타 공장 차단 ─────

    private Zone zoneOfFactory(Long zoneId, Long factoryId) {
        Factory factory = mock(Factory.class);
        lenient().when(factory.getId()).thenReturn(factoryId);
        Zone zone = mock(Zone.class);
        lenient().when(zone.getId()).thenReturn(zoneId);
        lenient().when(zone.getFactory()).thenReturn(factory);
        return zone;
    }

    @Test
    void assertCanManageZone_system_admin_allowed() {
        assertDoesNotThrow(() -> accessControlService.assertCanManageZone(
                user(Role.SYSTEM_ADMIN, 1L, null), zoneOfFactory(100L, 10L)));
    }

    @Test
    void assertCanManageZone_factory_admin_same_factory_allowed() {
        assertDoesNotThrow(() -> accessControlService.assertCanManageZone(
                user(Role.FACTORY_ADMIN, 1L, 10L), zoneOfFactory(100L, 10L)));
    }

    @Test
    void assertCanManageZone_factory_admin_other_factory_forbidden() {
        assertThrows(AccessDeniedException.class, () -> accessControlService.assertCanManageZone(
                user(Role.FACTORY_ADMIN, 1L, 10L), zoneOfFactory(100L, 99L)));
    }

    @Test
    void assertCanManageZone_member_in_zone_allowed() {
        User member = user(Role.MEMBER, 2L, null);
        when(zoneUserRepository.existsByZoneIdAndUserId(100L, 2L)).thenReturn(true);

        assertDoesNotThrow(() -> accessControlService.assertCanManageZone(member, zoneOfFactory(100L, 10L)));
    }

    @Test
    void assertCanManageZone_member_not_in_zone_forbidden() {
        User member = user(Role.MEMBER, 2L, null);
        when(zoneUserRepository.existsByZoneIdAndUserId(100L, 2L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanManageZone(member, zoneOfFactory(100L, 10L)));
    }
}

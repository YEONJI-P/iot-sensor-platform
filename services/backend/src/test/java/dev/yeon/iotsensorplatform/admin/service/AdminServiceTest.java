package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.ApproveRequest;
import dev.yeon.iotsensorplatform.factory.entity.Factory;
import dev.yeon.iotsensorplatform.factory.entity.Zone;
import dev.yeon.iotsensorplatform.factory.entity.ZoneUser;
import dev.yeon.iotsensorplatform.factory.repository.ZoneRepository;
import dev.yeon.iotsensorplatform.factory.repository.ZoneUserRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock ZoneRepository zoneRepository;
    @Mock ZoneUserRepository zoneUserRepository;
    @Mock AccessControlService accessControlService;
    @InjectMocks AdminService adminService;

    private User caller(Role role, Long factoryId) {
        User u = mock(User.class);
        when(u.getRole()).thenReturn(role);
        if (factoryId != null) {
            Factory f = mock(Factory.class);
            when(f.getId()).thenReturn(factoryId);
            when(u.getFactory()).thenReturn(f);
        }
        return u;
    }

    private User pendingTarget(Long id, Long factoryId) {
        User u = mock(User.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.getStatus()).thenReturn(UserStatus.PENDING);
        if (factoryId != null) {
            Factory f = mock(Factory.class);
            lenient().when(f.getId()).thenReturn(factoryId);
            lenient().when(u.getFactory()).thenReturn(f);
        }
        return u;
    }

    private Zone zoneInFactory(Long zoneId, Long factoryId) {
        Zone z = mock(Zone.class);
        lenient().when(z.getId()).thenReturn(zoneId);
        Factory f = mock(Factory.class);
        lenient().when(f.getId()).thenReturn(factoryId);
        lenient().when(z.getFactory()).thenReturn(f);
        return z;
    }

    @Test
    void factory_admin은_factory_admin_역할을_부여할_수_없다() {
        User caller = caller(Role.FACTORY_ADMIN, 1L);
        User target = pendingTarget(10L, 1L);
        when(userRepository.findByEmployeeId("MGR")).thenReturn(Optional.of(caller));
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () ->
                adminService.approveUser(10L, new ApproveRequest(Role.FACTORY_ADMIN, List.of()), "MGR"));

        verify(target, never()).approve(any());
    }

    @Test
    void system_admin은_factory_admin_역할과_구역을_배정할_수_있다() {
        User caller = caller(Role.SYSTEM_ADMIN, null);
        User target = pendingTarget(10L, null);
        Zone zone = zoneInFactory(5L, 2L);
        when(userRepository.findByEmployeeId("ADMIN")).thenReturn(Optional.of(caller));
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(zoneRepository.findById(5L)).thenReturn(Optional.of(zone));
        when(zoneUserRepository.existsByZoneIdAndUserId(5L, 10L)).thenReturn(false);

        adminService.approveUser(10L, new ApproveRequest(Role.FACTORY_ADMIN, List.of(5L)), "ADMIN");

        verify(target).approve(Role.FACTORY_ADMIN);
        verify(target).assignFactory(zone.getFactory());
        verify(zoneUserRepository).save(any(ZoneUser.class));
    }

    @Test
    void factory_admin은_자기공장_구역으로_member를_배정한다() {
        User caller = caller(Role.FACTORY_ADMIN, 1L);
        User target = pendingTarget(10L, 1L);
        Zone zone = zoneInFactory(5L, 1L);
        when(userRepository.findByEmployeeId("MGR")).thenReturn(Optional.of(caller));
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(zoneRepository.findById(5L)).thenReturn(Optional.of(zone));
        when(zoneUserRepository.existsByZoneIdAndUserId(5L, 10L)).thenReturn(false);

        adminService.approveUser(10L, new ApproveRequest(Role.MEMBER, List.of(5L)), "MGR");

        verify(target).approve(Role.MEMBER);
        verify(zoneUserRepository).save(any(ZoneUser.class));
    }

    @Test
    void 타공장_구역_배정은_거부된다() {
        User caller = caller(Role.FACTORY_ADMIN, 1L);
        User target = pendingTarget(10L, 1L);
        Zone otherZone = zoneInFactory(9L, 2L);
        when(userRepository.findByEmployeeId("MGR")).thenReturn(Optional.of(caller));
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(zoneRepository.findById(9L)).thenReturn(Optional.of(otherZone));
        doThrow(new AccessDeniedException("본인 공장의 구역만 관리할 수 있어요"))
                .when(accessControlService).assertCanManageZone(caller, otherZone);

        assertThrows(AccessDeniedException.class, () ->
                adminService.approveUser(10L, new ApproveRequest(Role.MEMBER, List.of(9L)), "MGR"));

        verify(target, never()).approve(any());
        verify(zoneUserRepository, never()).save(any());
    }
}

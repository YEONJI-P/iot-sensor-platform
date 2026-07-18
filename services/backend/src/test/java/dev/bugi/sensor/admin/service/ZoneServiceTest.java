package dev.bugi.sensor.admin.service;

import dev.bugi.sensor.admin.dto.ZoneResponse;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.repository.FactoryRepository;
import dev.bugi.sensor.factory.repository.ZoneRepository;
import dev.bugi.sensor.factory.repository.ZoneUserRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock ZoneRepository zoneRepository;
    @Mock FactoryRepository factoryRepository;
    @Mock ZoneUserRepository zoneUserRepository;
    @Mock UserRepository userRepository;
    @Mock AccessControlService accessControlService;

    @InjectMocks ZoneService zoneService;

    @Test
    void accessible_zone_list_reuses_access_control_scope() {
        User user = mock(User.class);
        Factory factory = mock(Factory.class);
        Zone zone = mock(Zone.class);
        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleZones(user)).thenReturn(List.of(zone));
        when(zone.getId()).thenReturn(1L);
        when(zone.getFactory()).thenReturn(factory);
        when(factory.getId()).thenReturn(10L);
        when(factory.getName()).thenReturn("1공장");
        when(zone.getName()).thenReturn("A구역");

        List<ZoneResponse> result = zoneService.getAccessible("EMP001");

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getFactoryId()).isEqualTo(10L);
            assertThat(response.getName()).isEqualTo("A구역");
        });
        verify(accessControlService).getAccessibleZones(user);
    }

    @Test
    void admin_zone_list_uses_the_same_accessible_scope() {
        User user = mock(User.class);
        when(userRepository.findByEmployeeId("ADMIN")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleZones(user)).thenReturn(List.of());

        assertThat(zoneService.getAll("ADMIN")).isEmpty();

        verify(accessControlService).getAccessibleZones(user);
    }
}
